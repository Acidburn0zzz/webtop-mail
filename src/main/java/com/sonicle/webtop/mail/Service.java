/*
 * webtop-mail is a WebTop Service developed by Sonicle S.r.l.
 * Copyright (C) 2014 Sonicle S.r.l.
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License version 3 as published by
 * the Free Software Foundation with the addition of the following permission
 * added to Section 15 as permitted in Section 7(a): FOR ANY PART OF THE COVERED
 * WORK IN WHICH THE COPYRIGHT IS OWNED BY SONICLE, SONICLE DISCLAIMS THE
 * WARRANTY OF NON INFRINGEMENT OF THIRD PARTY RIGHTS.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301 USA.
 *
 * You can contact Sonicle S.r.l. at email address sonicle@sonicle.com
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License version 3.
 *
 * In accordance with Section 7(b) of the GNU Affero General Public License
 * version 3, these Appropriate Legal Notices must retain the display of the
 * "Powered by Sonicle WebTop" logo. If the display of the logo is not reasonably
 * feasible for technical reasons, the Appropriate Legal Notices must display
 * the words "Powered by Sonicle WebTop".
 */
package com.sonicle.webtop.mail;

import com.sonicle.commons.LangUtils;
import java.nio.*;
import java.nio.channels.*;
import com.sonicle.commons.OldUtils;
import com.sonicle.commons.db.DbUtils;
import com.sonicle.commons.web.json.JsonResult;
import com.sonicle.mail.imap.SonicleIMAPFolder;
import com.sonicle.mail.sieve.*;
import com.sonicle.security.Principal;
import com.sonicle.security.AuthenticationDomain;
import com.sonicle.webtop.core.WT;
import com.sonicle.webtop.core.bol.OUser;
import com.sonicle.webtop.core.dal.UserDAO;
//import com.sonicle.webtop.core.*;
import com.sonicle.webtop.core.sdk.*;
import com.sonicle.webtop.mail.bol.ONote;
import com.sonicle.webtop.mail.bol.OUserMap;
import com.sonicle.webtop.mail.dal.FilterDAO;
import com.sonicle.webtop.mail.dal.NoteDAO;
import com.sonicle.webtop.mail.dal.ScanDAO;
import com.sonicle.webtop.mail.dal.UserMapDAO;
// TODO: Fix imported classes
//import com.sonicle.webtop.Mailcard;
//import com.sonicle.webtop.EditingSession;
//import com.sonicle.webtop.bol.OServiceSetting;
//import com.sonicle.webtop.bol.OUser;
//import com.sonicle.webtop.bol.OWorkgroup;
//import com.sonicle.webtop.bol.WebTopSettings;
//import com.sonicle.webtop.bol.js.JsSharingRight;
//import com.sonicle.webtop.contacts.*;
//import com.sonicle.webtop.dal.WebTopDb;
//import com.sonicle.webtop.mail.bol.JsMailcard;
//import com.sonicle.webtop.mail.bol.JsQuickPart;
//import com.sonicle.webtop.mail.dal.InboxMailFiltersDb;
//import com.sonicle.webtop.mail.dal.SentMailFiltersDb;
//import com.sonicle.webtop.profiledata.ProfileDataProviderBase;
//import com.sonicle.webtop.profiledata.ProfilePersonalInfo;
//import com.sonicle.webtop.setting.SettingsManager;
//import com.sonicle.webtop.util.*;
//import com.sonicle.webtop.vfs.VFSService;
import com.sun.mail.imap.*;
import com.sun.mail.util.PropUtil;
import java.io.*;
import java.sql.*;
import java.text.DateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.jar.*;
import javax.activation.*;
import javax.mail.*;
import javax.mail.Message.RecipientType;
import javax.mail.internet.*;
import javax.mail.search.MessageIDTerm;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;

public class Service extends com.sonicle.webtop.core.sdk.BaseService {
	
	public final static Logger logger = getLogger(Service.class);
	
	public static String flagStrings[] = {
		"red",
		"blue",
		"yellow",
		"green",
		"orange",
		"purple",
		"complete"
	};
	public static Flags flagsAll = new Flags();
	public static Flags oldFlagsAll = new Flags();
	public static HashMap<String, Flags> flagsHash = new HashMap<String, Flags>();
	public static HashMap<String, Flags> oldFlagsHash = new HashMap<String, Flags>();
	public static Flags flagNote = new Flags("mailnote");
	
	private FetchProfile FP = new FetchProfile();
	
	static {
		
		for (String fs : Service.flagStrings) {
			String oldfs = "flag" + fs;
			flagsAll.add(fs);
			oldFlagsAll.add(oldfs);
			Flags flags = new Flags();
			flags.add(fs);
			flagsHash.put(fs, flags);
			flags = new Flags();
			flags.add(oldfs);
			oldFlagsHash.put(fs, flags);
		}
	}
	
	static String startpre = "<PRE>";
	static String endpre = "</PRE>";
	private static String webtopTextMessage
			= "This email has been sent through Sonicle WebMail system [ http://www.sonicle.com ]";
	private static String webtopHTMLMessage = "This email has been sent through Sonicle WebMail system [ <A HREF='http://www.sonicle.com'>http://www.sonicle.com</A> ]";
	private static String unwantedTags[] = {"style"};
	
	private Session session;
	private Properties props;
	private Store store;
	private boolean disconnecting = false;
	private String sharedPrefixes[] = null;
	private char folderSeparator = 0;
	private String folderPrefix = null;
	
	private BasicEnvironment environment = null;
	private MailUserProfile mprofile;
	private MailServiceSettings mss = null;
	private MailUserSettings mus = null;
	private boolean validated = false;
	private int newMessageID = 0;
	private MailFoldersThread mft;
	private Sieve sieve = null;
	
	private HashMap<String, FolderCache> foldersCache = new HashMap<String, FolderCache>();
	private FolderCache fcRoot = null;
	private FolderCache[] fcShared = null;
	private FolderCache fcProvided = null;
	
	private String skipReplyFolders[] = null;
	private String skipForwardFolders[] = null;
	
	private static ArrayList<String> inlineableMimes = new ArrayList<String>();
	
	private HashMap<Integer, ArrayList<Attachment>> msgattach = new HashMap<Integer, ArrayList<Attachment>>();
	private HashMap<Integer, ArrayList<Attachment>> msgcloudattach = new HashMap<Integer, ArrayList<Attachment>>();
	private ArrayList<Attachment> emptyAttachments = new ArrayList<Attachment>();
	
	private AdvancedSearchThread ast = null;
	
	static {
		inlineableMimes.add("image/gif");
		inlineableMimes.add("image/jpeg");
		inlineableMimes.add("image/png");
//      inlineableMimes.add("image/tiff");
//      inlineableMimes.add("text/plain");
//      inlineableMimes.add("message/rfc822");
	}
	
	@Override
	public void initialize() {
		
		logger.debug("MailService.ID={}",getId());

		this.environment = getEnv();
		
		UserProfile profile = environment.getProfile();
		this.mss = new MailServiceSettings(environment.getCoreServiceSettings(), profile.getDomainId(), getManifest().getId());
		this.mus = new MailUserSettings(mss, profile.getDomainId(), profile.getUserId(), getManifest().getId());
		mprofile = new MailUserProfile(environment,this);
		fcProvided = new FolderCache(this, environment);
		this.props = System.getProperties();
		//this.props.setProperty("mail.imap.parse.debug", "true");
		this.props.setProperty("mail.smtp.host", mss.getSmtpHost());
		this.props.setProperty("mail.smtp.port", mss.getSmtpPort());
		//this.props.setProperty("mail.socket.debug", "true");
		this.props.setProperty("mail.imaps.ssl.trust", "*");
		this.props.setProperty("mail.imap.folder.class", "com.sonicle.mail.imap.SonicleIMAPFolder");
		this.props.setProperty("mail.imaps.folder.class", "com.sonicle.mail.imap.SonicleIMAPFolder");
		//support idle events
		this.props.setProperty("mail.imap.enableimapevents", "true");
		
		folderPrefix = mprofile.getFolderPrefix();
		String protocol = mprofile.getMailProtocol();
		
		FP.add(FetchProfile.Item.ENVELOPE);
		FP.add(FetchProfile.Item.FLAGS);
		FP.add(FetchProfile.Item.CONTENT_INFO);
		FP.add("Message-ID");
		FP.add("X-Priority");
		if (profile.hasDocumentManagement()) {
			FP.add("X-WT-Archived");
		}
		
		session = Session.getDefaultInstance(props, null);
		//session.setDebug(true);

		try {
			store = session.getStore(protocol);
		} catch (NoSuchProviderException exc) {
			logger.error("Cannot create mail store for {}", profile.getUserId(), exc);
		}
		
		mft = new MailFoldersThread(this, environment);
		mft.setCheckAll(mprofile.isScanAll());
		mft.setSleepInbox(mprofile.getScanSeconds());
		mft.setSleepCycles(mprofile.getScanCycles());
		try {
			mft.abort();
			checkStoreConnected();

			//prepare special folders if not existant
			if (mss.isAutocreateSpecialFolders()) {
				checkCreateFolder(mprofile.getFolderSent());
				checkCreateFolder(mprofile.getFolderDrafts());
				checkCreateFolder(mprofile.getFolderTrash());
				checkCreateFolder(mprofile.getFolderSpam());
			}
			
			skipReplyFolders = new String[]{
				mprofile.getFolderDrafts(),
				mprofile.getFolderSent(),
				mprofile.getFolderSpam(),
				mprofile.getFolderTrash()
			};
			skipForwardFolders = new String[]{
				mprofile.getFolderSpam(),
				mprofile.getFolderTrash()
			};
			
			loadFoldersCache();
			mft.start();

			//check sieve script
			Thread t = new Thread(new Runnable() {
				public void run() {
					try {
						UserProfile profile = environment.getProfile();
						Sieve sieve = getSieve();
						SieveScript[] scripts = sieve.getScripts();
						if (scripts.length == 0) {
							logger.info("No Sieve scripts for {}. Creating from database.", profile.getUserId());
							Connection con = null;
							try {
								con = getConnection();
								MailFilters filters = getMailFilters(con, "INBOX", profile.getUserId(), profile.getDomainId());
								if (filters != null) {
									sieve.saveScript(filters, true);
								}
							} catch (SQLException exc) {
								logger.error("Error getting connection while trying to save Sieve script", exc);
							} finally {
								DbUtils.closeQuietly(con);
							}
						}
					} catch (Exception exc) {
						exc.printStackTrace();
					}
				}
			});
			t.start();
			
			setSharedSeen(mus.isSharedSeen());
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public MailServiceSettings getMailServiceSettings() {
		return this.mss;
	}
	
	public MailUserSettings getMailUserSettings() {
		return this.mus;	
	}
	
	public Session getMailSession() {
		return this.session;
	}
	
	public boolean isValid() throws MessagingException {
		if (!validated) {
			if (environment == null) {
				return false;
			}
			validateUser();
		}
		return validated;
	}
	
	public boolean checkStoreConnected() throws MessagingException {
		if (environment == null) {
			return false;
		}
		UserProfile profile = environment.getProfile();
		synchronized (profile) {
			if (!isConnected()) {
				return validateUser();
			}
			return true;
		}
	}
	
	private boolean connect() {
		try {
			if (store.isConnected()) {
				disconnecting = true;
				store.close();
			}
		} catch (Exception exc) {
			exc.printStackTrace();
		}
		boolean sucess = true;
		disconnecting = false;
		UserProfile profile = environment.getProfile();
		try {
			int port = mprofile.getMailPort();
			String mailHost = mprofile.getMailHost();
			String mailUsername = mprofile.getMailUsername();
			String mailPassword = mprofile.getMailPassword();
			//wta.log("Store.connect to "+mailHost+" as "+mailUsername+" / "+mailPassword);
			if (port > 0) {
				store.connect(mailHost, port, mailUsername, mailPassword);
			} else {
				store.connect(mailHost, mailUsername, mailPassword);
			}
			folderSeparator = getDefaultFolder().getSeparator();
			Folder un[] = store.getUserNamespaces("");
			sharedPrefixes = new String[un.length];
			int ix = 0;
			for (Folder sp : un) {
				String s = sp.getFullName();
				//if (s.endsWith(""+folderSeparator)) s=s.substring(0,s.length()-1);
				sharedPrefixes[ix] = s;
				++ix;
			}
		} catch (MessagingException exc) {
			logger.error("Error connecting to the mail server", exc);
			sucess = false;
		}
		
		return sucess;
		
	}
	
	private boolean isConnected() {
		if (store == null) {
			return false;
		}
		return store.isConnected();
	}
	
	public boolean disconnect() {
		
		try {
			if (store.isConnected()) {
				disconnecting = true;
				store.close();
			}
			
		} catch (MessagingException ex) {
			ex.printStackTrace();
		}
		
		return true;
		
	}

	/**
	 * Validate the user login and password through the email server
	 *
	 */
	private boolean validateUser() throws MessagingException {
		validated = connect();
		return validated;
	}

	/**
	 * Logout
	 *
	 */
	public boolean logout() {
		// Disconnect from email server
		cleanup();
		return true;
	}
	
	public FetchProfile getMessageFetchProfile() {
		return FP;
	}

  // TODO: how to save options?!?!
/*  @Override
	 public void saveOptions(Hashtable params) {
	 WebTopApp wta=environment.getWebTopApp();
	 WebTopSession wts=environment.getWebTopSession();
	 UserProfile profile=environment.getUserProfile();
	 SettingsManager sm = wta.getSettingsManager();

	 String fontface=(String)params.get("fontface");
	 String fontsize=(String)params.get("fontsize");
	 String email=(String)params.get("email");
	 String askreceipt=(String)params.get("askreceipt");
	 String mailprotocol=null;
	 String mailhost=null;
	 String mailport=null;
	 String mailuser=null;
	 String mailpassword=null;
	 String folderprefix=null;
	 String foldersent=null;
	 String folderdrafts=null;
	 String foldertrash=null;
	 String folderspam=null;
	 String docmgt=null;
	 String docmgt2=null;
	 String docmgtFolder=null;
	 String scanall=null;
	 String sharedseen=null;
	 String docmgtwt=null;
	 String sharedsort=(String)params.get("sharedsort");
	 //System.out.println("sharedsort="+sharedsort);

	 boolean restart=false;
      
	 if (wts.isAdministrator()) {
	 mailprotocol=(String)params.get("mailprotocol");
	 mailhost=(String)params.get("mailhost");
	 mailport=(String)params.get("mailport");
	 mailuser=(String)params.get("mailuser");
	 mailpassword=(String)params.get("mailpassword");
	 folderprefix=(String)params.get("folderprefix");
	 foldersent=(String)params.get("foldersent");
	 folderdrafts=(String)params.get("folderdrafts");
	 foldertrash=(String)params.get("foldertrash");
	 folderspam=(String)params.get("folderspam");
	 docmgt=(String)params.get("docmgt");
	 docmgt2=(String)params.get("docmgt2");
	 docmgtFolder=(String)params.get("docmgtfolder");
	 docmgtwt=(String)params.get("docmgtwt");
	 scanall=(String)params.get("scanall");
	 }
	 sharedseen=(String)params.get("sharedseen");
	 String scansecs=(String)params.get("scansecs");
	 String scancycles=(String)params.get("scancycles");

	 SQLBuffer sb1=new SQLBuffer(SQLBuffer.Type.UPDATE,"users");
	 SQLBuffer sb2=new SQLBuffer(SQLBuffer.Type.UPDATE,"mailsettings");
	 if (email!=null) {
	 sb1.addStringField("email", email);
	 sb2.addStringField("email", email);
	 profile.setEmailAddress(email);
	 }
	 if (fontface!=null) {
	 sb1.addStringField("fontface", fontface);
	 sb2.addStringField("fontface", fontface);
	 profile.setFontFace(fontface);
	 }
	 if (fontsize!=null) {
	 sb1.addStringField("fontsize", fontsize);
	 sb2.addStringField("fontsize", fontsize);
	 profile.setFontSize(fontsize);
	 }
	 if (askreceipt!=null) { 
	 if (askreceipt.equals("true")) {
	 sb1.addStringField("mailreceipt", "Y");
	 sb2.addBooleanField("receipt", true);
	 profile.setAskReceipt(true);
	 } else {
	 sb1.addStringField("mailreceipt", null);
	 sb2.addBooleanField("receipt", false);
	 profile.setAskReceipt(false);
	 }
	 }
	 if (mailprotocol!=null) {
	 sb1.addStringField("mailprotocol", mailprotocol);
	 sb2.addStringField("protocol", mailprotocol);
	 restart=true;
	 profile.setMailProtocol(mailprotocol);
	 }
	 if (mailhost!=null) {
	 sb1.addStringField("mailhost", mailhost);
	 sb2.addStringField("host", mailhost);
	 restart=true;
	 profile.setMailHost(mailhost);
	 }
	 if (mailport!=null) {
	 sb1.addNumberField("mailport", mailport);
	 sb2.addNumberField("port", mailport);
	 restart=true;
	 profile.setMailPort(Integer.parseInt(mailport));
	 }
	 if (mailuser!=null) {
	 sb1.addStringField("mailusername", mailuser);
	 sb2.addStringField("username", mailuser);
	 restart=true;
	 profile.setMailUsername(mailuser);
	 }
	 if (mailpassword!=null) {
	 Principal p=profile.getPrincipal();
	 String credential=p.getCredential();
	 String cmailpassword=mailpassword;
	 try {
	 cmailpassword=wta.cipher(mailpassword,credential);
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 }
	 sb1.addStringField("mailpassword", cmailpassword);
	 sb2.addStringField("password", cmailpassword);
	 restart=true;
	 profile.setMailPassword(mailpassword);
	 }
	 if (folderprefix!=null) {
	 sb1.addStringField("folderprefix", folderprefix);
	 sb2.addStringField("folderprefix", folderprefix);
	 restart=true;
	 profile.setFolderPrefix(folderprefix);
	 }
	 if (foldersent!=null) {
	 sb1.addStringField("mailsent", foldersent);
	 sb2.addStringField("foldersent", foldersent);
	 restart=true;
	 profile.setFolderSent(foldersent);
	 }
	 if (folderdrafts!=null) {
	 sb1.addStringField("maildrafts", folderdrafts);
	 sb2.addStringField("folderdrafts", folderdrafts);
	 restart=true;
	 profile.setFolderDrafts(folderdrafts);
	 }
	 if (foldertrash!=null) {
	 sb1.addStringField("mailtrash", foldertrash);
	 sb2.addStringField("foldertrash", foldertrash);
	 restart=true;
	 profile.setFolderTrash(foldertrash);
	 }
	 if (folderspam!=null) {
	 sb1.addStringField("mailspam", folderspam);
	 sb2.addStringField("folderspam", folderspam);
	 restart=true;
	 profile.setFolderSpam(folderspam);
	 }
	 if (sharedsort!=null) {
	 sb1.addStringField("mailsharedsort", sharedsort);
	 sb2.addStringField("sharedsort", sharedsort);
	 restart=true;
	 profile.setMailSharedSort(sharedsort);
	 }
	 if (docmgt!=null) {
	 if (docmgt.equals("true")) {
	 sb1.addStringField("docmgt", "Y");
	 profile.setDocumentManagement(true);
	 } else {
	 sb1.addStringField("docmgt", null);
	 profile.setDocumentManagement(false);
	 }
	 }
	 if (docmgt2!=null) {
	 if (docmgt2.equals("true")) {
	 sb1.addStringField("docmgt2", "Y");
	 profile.setStructuredArchiving(true);
	 } else {
	 sb1.addStringField("docmgt2", null);
	 profile.setStructuredArchiving(false);
	 }
	 }
	 if (docmgtFolder!=null) {
	 sb1.addStringField("docmgtfolder", docmgtFolder);
	 profile.setDocumentManagementFolder(docmgtFolder);
	 }

	 if (scanall!=null) {
	 if (scanall.equals("true")) {
	 sb1.addStringField("scanall", "Y");
	 profile.setScanAll(true);
	 mft.setCheckAll(true);
	 } else {
	 sb1.addStringField("scanall", "N");
	 profile.setScanAll(false);
	 mft.setCheckAll(false);
	 }
	 }
	 if(sharedseen != null) applySharedSeen(Boolean.valueOf(sharedseen));

	 if (scansecs!=null) {
	 int ss=Integer.parseInt(scansecs);
	 if (ss<30) { ss=30; scansecs="30"; }
	 sb1.addNumberField("scansecs", scansecs);
	 profile.setScanSeconds(ss);
	 mft.setSleepInbox(ss);
	 }

	 if (scancycles!=null) {
	 sb1.addNumberField("scancycles", scancycles);
	 int sc=Integer.parseInt(scancycles);
	 profile.setScanSeconds(sc);
	 mft.setSleepCycles(sc);
	 }
      
	 if (docmgtwt!=null){
	 wtd.setServiceSetting("mail", this.environment.getUserProfile(), "docmgtwt", docmgtwt);
	 }
      
	 Connection con=null;
	 Statement stmt=null;
	 try {
	 con=wta.getMainConnection();
	 stmt=con.createStatement();
	 if (sb1.hasFields()) {
	 sb1.setWhere("iddomain='"+wtd.getLocalIDDomain()+"' and login='"+profile.getUser()+"'");
	 try { stmt.executeUpdate(sb1.getSQL()); } catch(SQLException exc) {}
	 }
	 if (sb2.hasFields()) {
	 sb2.setWhere("idprincipal="+profile.getIdPrincipal());
	 try { stmt.executeUpdate(sb2.getSQL()); } catch(SQLException exc) {}
	 }
	 } catch(SQLException exc) {
	 exc.printStackTrace();
	 } finally {
	 if (stmt!=null) try { stmt.close(); } catch(Exception exc) {}
	 if (con!=null) try { con.close(); } catch(Exception exc) {}
	 }

	 if (params.get("idents")!=null) {
	 String dn[]=null;
	 String em[]=null;
	 String bf[]=null;
	 String ty[]=null;
	 if(params.get("idents-email") instanceof String[]) {
	 dn=(String[])params.get("idents-displayname");
	 em=(String[])params.get("idents-email");
	 bf=(String[])params.get("idents-basefolder");
	 ty=(String[])params.get("idents-type");
	 } else {
	 dn=new String[]{(String)params.get("idents-displayname")};
	 em=new String[]{(String)params.get("idents-email")};
	 bf=new String[]{(String)params.get("idents-basefolder")};
	 ty=new String[]{(String)params.get("idents-type")};
	 }
		  
	 PreparedStatement stmt1 = null;
	 try {
	 con=wta.getMainConnection();
	 stmt=con.createStatement();
	 String login=profile.getUser();
	 String sql="delete from identities where iddomain='"+wtd.getLocalIDDomain()+"' and login='"+login+"'";
	 //System.out.println(sql);
	 stmt.executeUpdate(sql);
	 //profile.clearIdentities();
	 stmt1 = con.prepareStatement("select count(*) from workgroups as wg inner join users as us on (wg.groupname = us.login) where (us.email = ?) AND (wg.mail <> 'F')");
	 Integer count = null;
	 for(int i=0;i<dn.length;++i) {
	 if(StringUtils.isEmpty(em[i])) continue; // To skip empty records! How?
	 if(ty[i].equals(Identity.TYPE_AUTO)) continue; // Skip automatic addresses! Why are this records passed back? They aren't dirty! Mmm..
	 StatementUtils.setString(stmt1, 1, em[i]);
	 count = StatementUtils.getIntQueryValue(stmt1);
	 if(count > 0) {
	 logger.warn("Unable to use [{}] as new identity. Address already shared.", em[i]);
	 continue;
	 }
	 stmt.executeUpdate(
	 "insert into identities (iddomain,login,email,displayname,mainfolder) values ("+
	 "'"+wtd.getLocalIDDomain()+"',"+
	 "'"+login+"',"+
	 "'"+Utils.getSQLString(em[i])+"',"+
	 "'"+Utils.getSQLString(dn[i])+"',"+
	 "'"+Utils.getSQLString(bf[i])+"'"+
	 ")"
	 );
	 //profile.addIdentity(Identity.TYPE_USER, dn[i], em[i], bf[i]);
	 }
	 profile.buildIdentities(con);

	 } catch(Exception exc) {
	 exc.printStackTrace();
	 } finally {
	 if (stmt1!=null) try { stmt1.close(); } catch(Exception exc) {}
	 if (stmt!=null) try { stmt.close(); } catch(Exception exc) {}
	 if (con!=null) try { con.close(); } catch(Exception exc) {}
	 }
	 }
	 if (restart) {
	 this.logout();
	 this.initialize(environment);
	 }
	 }*/
  // TODO: Sharing management
/*  @Override
	 public void addSharing(OWorkgroup workgroup) throws Exception {
	 setFolderSharing(workgroup.login, workgroup.resource, workgroup.parameters);
	 }
  
	 @Override
	 public void removeSharing(OWorkgroup workgroup) throws Exception {
	 removeFolderSharing(workgroup.login, workgroup.resource);
	 }*/
  // TODO: applySharedSeen!
/*	private void applySharedSeen(boolean value) {
	 UserProfile profile = environment.getProfile();
	 boolean old = mus.isSharedSeen();
	 sm.setUserSetting(profile, "mail", Settings.SHARED_SEEN, String.valueOf(value));
	 try {
	 setSharedSeen(value);
	 } catch (MessagingException ex) {
	 logger.error("Unable to apply shared seen to {}. Restoring setting!", profile.getUser());
	 sm.setUserSetting(profile, "mail", Settings.SHARED_SEEN, String.valueOf(old));
	 }
	 }*/
	public MailFoldersThread getMailFoldersThread() {
		return mft;
	}

	/*  public Vector getFoldersCache(boolean refresh) {
	 return msm.getFoldersCache(refresh);
	 }*/
	public Sieve getSieve() {
		UserProfile profile = environment.getProfile();
		String mailHost = mprofile.getMailHost();
		String mailUsername = mprofile.getMailUsername();
		String mailPassword = mprofile.getMailPassword();
		if (mailHost == null || mailUsername == null || mailPassword == null) {
			return null;
		}
		if (sieve != null) {
			sieve.setHostname(mailHost);
			sieve.setUsername(mailUsername);
			sieve.setPassword(mailPassword);
		} else {
			sieve = new Sieve(mailHost, mailUsername, mailPassword);
		}
		return sieve;
		
	}
	
	public char getFolderSeparator() throws MessagingException {
		return folderSeparator;
	}
	
	public FolderCache[] getSharedFoldersCache() throws MessagingException {
		if (fcShared == null) {
			if (sharedPrefixes != null) {
				String sf[] = sharedPrefixes;
				fcShared = new FolderCache[sf.length];
				for (int i = 0; i < sf.length; ++i) {
					fcShared[i] = getFolderCache(sf[i]);
				}
			}
		}
		return fcShared;
	}
	
	public FolderCache getRootFolderCache() {
		return fcRoot;
	}
	
	public Set<Entry<String, FolderCache>> getFolderCacheEntries() {
		return foldersCache.entrySet();
	}
	
	public FolderCache getFolderCache(String foldername) throws MessagingException {
		return foldersCache.get(foldername);
	}
	
	public void setFolderPrefix(String prefix) {
		folderPrefix = prefix;
	}
	
	public String getFolderPrefix() {
		return folderPrefix;
	}
	
	public void archiveMessages(FolderCache from, int nids[], String idcategory, String idsubcategory) throws MessagingException, FileNotFoundException, IOException {
		UserProfile profile = environment.getProfile();
		String archiveto = mss.getArchivePath();
		for (int nid : nids) {
			Message msg = from.getMessage(nid);
			
			String id = getMessageID(msg);
			if (id.startsWith("<")) {
				id = id.substring(1, id.length() - 1);
			}
			id = id.replaceAll("\\\\", "_");
			id = id.replaceAll("/", "_");
			String filename = archiveto + "/" + id + ".eml";
			String txtname = archiveto + "/" + id + ".txt";
			File file = new File(filename);
			File txtfile = new File(txtname);
			//Only if spool file does not exists
			if (!file.exists()) {
				
				String emailfrom = "nomail@nodomain.it";
				Address a[] = msg.getFrom();
				if (a != null && a.length > 0) {
					InternetAddress sender = ((InternetAddress) a[0]);
					emailfrom = sender.getAddress();
				}
				String emailto = "nomail@nodomain.it";
				a = msg.getRecipients(Message.RecipientType.TO);
				if (a != null && a.length > 0) {
					InternetAddress to = ((InternetAddress) a[0]);
					emailto = to.getAddress();
				}
				String subject = msg.getSubject();
				java.util.Date date = msg.getReceivedDate();
				Calendar cal = Calendar.getInstance();
				cal.setTime(date);
				int dd = cal.get(Calendar.DAY_OF_MONTH);
				int mm = cal.get(Calendar.MONTH) + 1;
				int yyyy = cal.get(Calendar.YEAR);
				String sdd = dd < 10 ? "0" + dd : "" + dd;
				String smm = mm < 10 ? "0" + mm : "" + mm;
				String syyyy = "" + yyyy;
				
				FileOutputStream fos = new FileOutputStream(file);
				msg.writeTo(fos);
				fos.close();
				
				PrintWriter pw = new PrintWriter(txtfile);
				pw.println(emailfrom);
				pw.println(emailto);
				pw.println(subject);
				pw.println(sdd + "/" + smm + "/" + syyyy);
				pw.println(profile.getUserId());
				pw.println(idcategory);
				pw.println(idsubcategory);
				pw.close();
			}
		}
		from.markArchivedMessages(nids);
	}
	
	public void archiveMessagesWt(FolderCache from, int nids[], String idcategory, String idsubcategory, String customer_id) throws MessagingException, FileNotFoundException, IOException {
		UserProfile profile = environment.getProfile();
		String archiveto = mss.getArchivePath();
		for (int nid : nids) {
			Message msg = from.getMessage(nid);
			
			String id = getMessageID(msg);
			if (id.startsWith("<")) {
				id = id.substring(1, id.length() - 1);
			}
			id = id.replaceAll("\\\\", "_");
			id = id.replaceAll("/", "_");
			String filename = id + ".eml";
			String path = archiveto + "/" + filename;
			File file = new File(path);
			//Only if spool file does not exists
			if (!file.exists()) {
				FileOutputStream fos = new FileOutputStream(file);
				msg.writeTo(fos);
				fos.close();
			}
			String subject = msg.getSubject();
			String emailfrom = "nomail@nodomain.it";
			Address a[] = msg.getFrom();
			if (a != null && a.length > 0) {
				InternetAddress sender = ((InternetAddress) a[0]);
				emailfrom = sender.getAddress();
			}
			String name = subject;
			//String request_id = saveMailInRequestsDrm(idcategory,customer_id);
			String category = idcategory;
			if (idsubcategory != null && !idsubcategory.equals("")) {
				category += "/" + idsubcategory;
			}
			String request_id = saveMailInRequestsDrm("", "", category, "", customer_id, "", "", "", "", "", "");
			name = name + ".eml";
			String generic_id = saveMailInGenericDocuments(category, customer_id, subject);
			saveMailInDocumentsDrm(path, name, request_id, generic_id);
			saveTrackingHistoryDrm(request_id);
		}
		from.markArchivedMessages(nids);
	}
	
	public String saveMailInGenericDocuments(String category_id, String customer_id, String subject) {
		String generic_document_id = "";
		Connection con = null;
		Statement stmt = null;
		try {
			con = getConnection();
			stmt = con.createStatement();
			System.out.println("category_id=" + category_id);
			Category cat = getCategory(category_id);
			if (cat != null) {
				category_id = cat.category_id;
			}
			generic_document_id = getDrmGenericDocumentsId(con);
			String sql = " insert into drm_generic_documents ("
					+ "generic_document_id,"
					+ "senderrecipient,"
					+ "iddomain,"
					+ "request_date,"
					+ "login,"
					+ "category_id,";
			if (customer_id != null && !customer_id.equals("")) {
				sql += "customer_id,";
			}
			sql += "severity,";
			sql += "description,";
			sql += "status,";
			sql += "type_document  ";
			sql += ") values (";
			sql += generic_document_id + ",";
			sql += "'" + environment.getProfile().getUserId() + "',";
			sql += "'" + environment.getProfile().getDomainId() + "',";
			sql += "now(),";
			sql += "'" + environment.getProfile().getUserId() + "',";
			sql += category_id + ",";
			if (customer_id != null && !customer_id.equals("")) {
				sql += "'" + customer_id + "',";
			}
			sql += "'S',";
			sql += "'" + subject + "',";
			sql += "'O',";
			sql += "'I'";			
			sql += ")";
			System.out.println("generic=" + sql);
			stmt.executeUpdate(sql);			
		} catch (Exception ex) {
			ex.printStackTrace(System.out);
		}
		return generic_document_id;
	}
	
	String getDrmGenericDocumentsId(Connection con) throws SQLException {
		String document_id = "";
		Statement stmt = con.createStatement();
		ResultSet rset = null;
		if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("oracle")) {
			rset = stmt.executeQuery("SELECT SEQ_DRM_GENERIC_DOCUMENTS.nextval AS DOCUMENT_ID from DUAL");
		} else if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("postgresql")) {
			rset = stmt.executeQuery("SELECT nextval('SEQ_DRM_GENERIC_DOCUMENTS') AS DOCUMENT_ID from DUAL");
		}
		rset.next();
		document_id = rset.getString("document_id");
		rset.close();
		stmt.close();
		return document_id;
	}	
	
	String getDrmTrackingDocumentsId(Connection con) throws SQLException {
		String document_id = "";
		Statement stmt = con.createStatement();
		ResultSet rset = null;
		if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("oracle")) {
			rset = stmt.executeQuery("SELECT SEQ_DRM_DOCUMENTS.nextval AS DOCUMENT_ID from DUAL");
		} else if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("postgresql")) {
			rset = stmt.executeQuery("SELECT nextval('SEQ_DRM_DOCUMENTS') AS DOCUMENT_ID from DUAL");
		}
		rset.next();
		document_id = rset.getString("document_id");
		rset.close();
		stmt.close();
		return document_id;
	}
	
	public boolean saveMailInDocumentsDrm(String path, String filename, String request_id, String generic_id) {
		boolean success = false;
		Statement stmt = null;
		Connection con = null;
		FileInputStream fi = null;
		try {
			con = getConnection();
			String id = getDrmTrackingDocumentsId(con);
			stmt = con.createStatement();
			String sql = "INSERT INTO DRM_DOCUMENTS (DOCUMENT_ID,REQUEST_ID,FILENAME,FILE,REVISION,FROMGENERICDOCUMENTS,DATE_DOCUMENT) values (" + id + "," + request_id + ",'" + filename + "',lo_import('" + path + "'),NOW()," + generic_id + ",NOW())";
			System.out.println("saveDocmail=" + sql);
			stmt.executeUpdate(sql);
			success = true;
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (fi != null) {
				try {
					fi.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
		return success;
	}
	
	String getDrmRequestsId(Connection con) throws SQLException {
		String requests_id = "";
		Statement stmt = con.createStatement();
		ResultSet rset = null;
		if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("oracle")) {
			rset = stmt.executeQuery("SELECT seq_requests.nextval as requests_id FROM DUAL");
		} else if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("postgresql")) {
			rset = stmt.executeQuery("SELECT nextval('seq_requests') as requests_id FROM DUAL");
		}
		rset.next();
		requests_id = rset.getString("requests_id");
		rset.close();
		stmt.close();
		return requests_id;
	}
	
	String getDrmHistoryId(Connection con) throws SQLException {
		String history_id = "";
		Statement stmt = con.createStatement();
		ResultSet rset = null;
		if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("oracle")) {
			rset = stmt.executeQuery("SELECT SEQ_DRM_REQUESTS_HISTORY.nextval AS HISTORY_ID from DUAL");
		} else if (con.getMetaData().getDatabaseProductName().equalsIgnoreCase("postgresql")) {
			rset = stmt.executeQuery("SELECT nextval('SEQ_DRM_REQUESTS_HISTORY') AS HISTORY_ID from DUAL");
		}
		rset.next();
		history_id = rset.getString("history_id");
		rset.close();
		stmt.close();
		return history_id;
	}	

	public boolean saveTrackingHistoryDrm(String request_id) {
		Connection con = null;
		Statement stmt = null;
		boolean success = false;		
		try {			
			con = getConnection();
			String history_id = getDrmHistoryId(con);
			stmt = con.createStatement();
			String query = "";
			query += "insert into drm_requests_history (HISTORY_ID,REQUEST_ID     ,REQUEST_BY      ,ASSIGN_TO      ,REQUEST_DATE,LOGIN     ,CATEGORY_ID    ,RELEASE      ,SEVERITY      ,CUSTOMER_ID      ,STATISTIC_ID      ,ENVIRONMENT       ,DESCRIPTION     ,FULLDESCRIPTION      ,SIMULATION      ,SUGGESTION      ,STATUS,RESOLUTION,MOV_TYPE,CONTACT_ID,N_DOCUMENT,YEAR_DOCUMENT,REVISION)"
					+ " select " + history_id + ",REQUEST_ID     ,REQUEST_BY      ,ASSIGN_TO      ,REQUEST_DATE,LOGIN     ,CATEGORY_ID    ,RELEASE      ,SEVERITY      ,CUSTOMER_ID      ,STATISTIC_ID      ,ENVIRONMENT       ,DESCRIPTION     ,FULLDESCRIPTION      ,SIMULATION      ,SUGGESTION      ,STATUS,RESOLUTION,MOV_TYPE,CONTACT_ID,N_DOCUMENT,YEAR_DOCUMENT,NOW() from drm_requests where request_id=" + request_id;
			System.out.println("save=" + query);
			stmt.executeUpdate(query);
			success = true;
		} catch (SQLException exc) {
			exc.printStackTrace();
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
		return success;
	}
	
	class Category {

		String category_id;
		String description;
		String longdescription;
		String openevent;
		String status;
		String expired;
		String sequence_id;
		String year;
		String year_sequence;

		Category(String category_id,
				String description,
				String longdescription,
				String openevent,
				String status,
				String expired,
				String sequence_id,
				String year,
				String year_sequence) {
			this.category_id = category_id;
			this.description = description;
			this.longdescription = longdescription;
			this.openevent = openevent;
			this.status = status;
			this.expired = expired;
			this.sequence_id = sequence_id;
			this.year = year;
			this.year_sequence = year_sequence;
			
		}
	}
	
	public String saveMailInRequestsDrm(
			String request_by,
			String assign_to,
			String category_id,
			String severity,
			String customer_id,
			String statistic_id,
			String description,
			String fulldescription,
			String status,
			String mov_type,
			String contact_id) {
		Connection con = null;
		Statement stmt = null;
		String requests_id = "";
		
		try {			
			con = getConnection();
			requests_id = getDrmRequestsId(con);
			stmt = con.createStatement();
			Category cat = getCategory(category_id);
			String document_id = requests_id;
			Calendar date = Calendar.getInstance();
			String year = date.get(Calendar.YEAR) + "";
			if (cat != null) {
				if (cat.year_sequence.equals("true")) {
					document_id = cat.sequence_id;
					year = cat.year;
					updateCategorySequence(cat.sequence_id, cat.year, cat.category_id);
				}
				category_id = "'" + cat.category_id + "'";
				System.out.println("category_id=" + category_id);
			}
			String query = "insert into drm_requests (";
			query += " request_id,";
			query += " request_by,";
			query += " assign_to,";
			query += " request_date,";
			query += " login,";
			query += " category_id,";
			query += " severity,";
			query += " customer_id,";
			query += " statistic_id,";
			query += " description,";
			query += " fulldescription,";
			query += " status,";
			query += " mov_type,";
			query += " contact_id,";
			query += " n_document,";
			query += " year_document,";
			query += " iddomain";
			query += " ) values ( ";
			query += requests_id + ",";
			if (request_by == null || request_by.equals("")) {
				request_by = environment.getProfile().getUserId();
			}
			query += "'" + request_by + "',";
			if (assign_to == null || assign_to.equals("")) {
				query += "(select assign_to from drm_profiles where type='M'),";
			} else {
				query += "'" + assign_to + "',";
			}
			query += "now(),";
			query += "'" + environment.getProfile().getUserId() + "',";
			if (category_id == null || category_id.equals("")) {
				query += "(select category_id from drm_profiles where type='M'),";
			} else {
				query += category_id + ",";
			}
			if (severity == null || severity.equals("")) {
				query += "(select severity from drm_profiles where type='M'),";
			} else {
				query += "'" + severity + "',";
			}
			if (customer_id == null || customer_id.equals("")) {
				query += "(select customer_id from drm_profiles where type='M'),";
			} else {
				query += customer_id + ",";
			}
			if (description == null || description.equals("")) {
				query += "(select case description when '' then 'Email' else description end  from drm_profiles where type='M'),";
			} else {
				query += "'" + description + "',";
			}
			if (statistic_id == null || statistic_id.equals("")) {
				query += "(select statistic_id from drm_profiles where type='M'),";
			} else {
				query += statistic_id + ",";
			}
			if (fulldescription == null || fulldescription.equals("")) {
				query += "(select fulldescription from drm_profiles where type='M'),";
			} else {
				query += "'" + fulldescription + "',";
			}
			if (status == null || status.equals("")) {
				query += "(select status from drm_profiles where type='M'),";
			} else {
				query += "'" + status + "',";
			}
			if (mov_type == null || mov_type.equals("")) {
				query += "(select mov_type from drm_profiles where type='M'),";
			} else {
				query += "'" + mov_type + "',";
			}
			if (contact_id == null || contact_id.equals("")) {
				query += "(select contact_id from drm_profiles where type='M'),";
			} else {
				query += contact_id + ",";
			}
			query += "'" + document_id + "',";
			query += "'" + year + "',";
			query += "'" + environment.getProfile().getDomainId() + "' ";
			query += ")";
			System.out.println("mail=" + query);
			stmt.executeUpdate(query);
			
		} catch (SQLException exc) {
			exc.printStackTrace();
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
		return requests_id;
	}
	
	public void updateCategorySequence(String sequence, String year, String category_id) {
		Connection con = null;
		Statement stmt = null;
		try {
			con = getConnection();
			stmt = con.createStatement();
			
			String query = ("UPDATE DRM_CATEGORIES SET SEQUENCE_ID=" + sequence + ",YEAR='" + year + "' WHERE CATEGORY_ID=" + category_id);
			stmt.executeUpdate(query);
			
			con.close();
		} catch (SQLException exc) {
			exc.printStackTrace();
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
	}
	
	public Category getCategory(String category) {
		Connection con = null;
		Statement stmt = null;
		ResultSet rset = null;
		Category cat = null;
		try {
			if (category.equals("/")) {
				cat = new Category("0", "", "", "", "", "", "", "", "");
			} else {
				if (category.startsWith("/")) {
					category = category.substring(1);
				}
				
				con = getConnection();
				stmt = con.createStatement();
				String query = "SELECT * FROM DRM_CATEGORIES WHERE DESCRIPTION='" + category + "' and status!='D' for update";
				rset = stmt.executeQuery(query);
				if (rset.next()) {
					String category_id = rset.getString("category_id");
					String description = rset.getString("description");
					String longdescription = rset.getString("longdescription");
					String openevent = rset.getString("openevent");
					String status = rset.getString("status");
					String expired = rset.getString("expired");
					String sequence_id = rset.getString("sequence_id");
					String year = rset.getString("year");
					String year_sequence = rset.getString("year_sequence");
					cat = new Category(category_id, description, longdescription, openevent, status, expired, sequence_id, year, year_sequence);
				}
				
				if (cat != null) {
					Calendar date = Calendar.getInstance();
					String year = date.get(Calendar.YEAR) + "";
					if (cat.year_sequence.equals("true")) {
						if (cat.year.equals(year)) {
							cat.sequence_id = (Integer.parseInt(cat.sequence_id)) + 1 + "";
						} else {
							cat.sequence_id = "1";
						}
					}
					cat.year = year;
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			if (rset != null) {
				try {
					rset.close();
				} catch (Exception exc) {
				}
			}
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
		
		return cat;
	}
	
	public void moveMessages(FolderCache from, FolderCache to, int ids[]) throws MessagingException {
		from.moveMessages(ids, to);
	}
	
	public void copyMessages(FolderCache from, FolderCache to, int ids[]) throws MessagingException {
		from.copyMessages(ids, to);
	}
	
	public void deleteMessages(FolderCache from, int ids[]) throws MessagingException {
		from.deleteMessages(ids);
	}
	
	public void flagMessages(FolderCache from, int ids[], String flag) throws MessagingException {
		from.flagMessages(ids, flag);
	}
	
	public void clearMessagesFlag(FolderCache from, int ids[]) throws MessagingException {
		from.clearMessagesFlag(ids);
	}
	
	public void setMessagesSeen(FolderCache from, int ids[]) throws MessagingException {
		from.setMessagesSeen(ids);
	}
	
	public void setMessagesUnseen(FolderCache from, int ids[]) throws MessagingException {
		from.setMessagesUnseen(ids);
	}
	
	public String getShortFolderName(String fullname) {
		String shortname = fullname;
		if (fullname.startsWith(folderPrefix.toLowerCase()) || fullname.startsWith(folderPrefix.toUpperCase())) {
			shortname = fullname.substring(folderPrefix.length());
		}
		return shortname;
	}
	
	public String getLastFolderName(String fullname) {
		String lasttname = fullname;
		if (lasttname.indexOf(folderSeparator) >= 0) {
			lasttname = lasttname.substring(lasttname.lastIndexOf(folderSeparator) + 1);
		}
		return lasttname;
	}
	
	public boolean deleteFolder(String fullname) throws MessagingException {
		return deleteFolder(getFolder(fullname));
	}
	
	public boolean deleteFolder(Folder folder) throws MessagingException {
		for (Folder subfolder : folder.list()) {
			deleteFolder(subfolder);
		}
		boolean retval = folder.delete(true);
		if (retval) {
			destroyFolderCache(getFolderCache(folder.getFullName()));
		}
		return retval;
	}
	
	public boolean emptyFolder(String fullname) throws MessagingException {
		FolderCache fc = getFolderCache(fullname);
		FolderCache parent = fc.getParent();
		if (deleteFolder(fullname)) {
			destroyFolderCache(fc);
			Folder f = getFolder(fullname);
			f.create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
			fc = addFoldersCache(parent, f);
			if (fc != null) {
				return true;
			}
		}
		return false;
	}
	
	public FolderCache trashFolder(String fullname) throws MessagingException {
		return moveFolder(fullname, mprofile.getFolderTrash());
	}
	
	public FolderCache moveFolder(String source, String dest) throws MessagingException {
		Folder oldfolder = getFolder(source);
		String oldname = oldfolder.getName();
		Folder newfolder;
		if (dest != null && dest.trim().length() > 0) {
			String newname = dest + getFolderSeparator() + oldname;
			newfolder = getFolder(newname);
		} else {
			String prefix = getFolderPrefix();
			String newname = oldname;
			if (prefix != null) {
				newname = prefix + newname;
			}
			newfolder = getFolder(newname);
		}
		FolderCache fcsrc = getFolderCache(source);
		if (fcsrc != null) {
			destroyFolderCache(fcsrc);
		}
		boolean done = oldfolder.renameTo(newfolder);
		if (done) {
			if (dest != null) {
				FolderCache tfc = getFolderCache(newfolder.getParent().getFullName());
				return addFoldersCache(tfc, newfolder);
			} else {
				return addFoldersCache(fcRoot, newfolder);
			}
		}
		return null;
	}
	
	public String renameFolder(String orig, String newname) throws MessagingException {
		FolderCache fc = getFolderCache(orig);
		FolderCache fcparent = fc.getParent();
		Folder oldfolder = fc.getFolder();
		destroyFolderCache(fc);
		Folder newfolder = fcparent.getFolder().getFolder(newname);
		boolean done = oldfolder.renameTo(newfolder);
		if (!done) {
			throw new MessagingException("Rename failed");
		}
		addFoldersCache(fcparent, newfolder);
		return newfolder.getFullName();
	}
	
	private InternetAddress getInternetAddress(String email) throws UnsupportedEncodingException, AddressException {
		String address = null;
		String personal = null;
		int ix = email.indexOf('<');
		if (ix >= 0) {
			int ix2 = email.indexOf('>');
			personal = email.substring(0, ix).trim();
			address = email.substring(ix + 1, ix2);
			return new InternetAddress(address, personal, "UTF-8");
		}
		return new InternetAddress(email);
	}
	
	public Exception sendReceipt(String from, String to, String subject, String body) {
		return sendTextMsg(from, from, new String[]{to}, null, null, "Receipt: " + subject, body);
	}
	
	private Exception sendTextMsg(String fromAddr, String rplyAddr, String[] toAddr,
			String[] ccAddr,
			String[] bccAddr, String subject, String body) {
		
		return sendTextMsg(fromAddr,
				rplyAddr, toAddr, ccAddr, bccAddr, subject, body, null);
		
	}
	
	private Exception sendTextMsg(String fromAddr, String rplyAddr, String[] toAddr, String[] ccAddr, String[] bccAddr,
			String subject, String body, ArrayList<Attachment> attachments) {
		
		SimpleMessage smsg = new SimpleMessage(0);

		//set the TO recipients
		smsg.addTo(toAddr);

		//set the CC recipients
		smsg.addCc(ccAddr);

		//set BCC recipients
		smsg.addBcc(bccAddr);

		//set Reply To address
		if (rplyAddr != null && rplyAddr.length() > 0) {
			smsg.setReplyTo(rplyAddr);

			//set the subject
		}
		smsg.setSubject(subject);

		//set the content
		smsg.setContent(body);
		
		return sendMsg(fromAddr, smsg, attachments);
		
	}
	
	public boolean sendMsg(Message msg) {
		UserProfile profile = environment.getProfile();
		String sentfolder = mprofile.getFolderSent();
		try {
			Transport.send(msg);
			saveSent(msg, sentfolder);
			return true;
			
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
		
	}
	
	public Exception sendMsg(String from, SimpleMessage smsg, ArrayList<Attachment> attachments) {
		UserProfile profile = environment.getProfile();
		String sentfolder = mprofile.getFolderSent();
		Identity ident = smsg.getFrom();
		if (ident != null && ident.mainfolder != null && ident.mainfolder.trim().length() > 0) {
			String newsentfolder = ident.mainfolder + folderSeparator + getLastFolderName(sentfolder);
			try {
				Folder folder = getFolder(newsentfolder);
				if (folder.exists()) {
					sentfolder = newsentfolder;
				}
			} catch (MessagingException exc) {
				logger.error("Error on identity {}/{} Sent Folder", profile.getUserId(), ident.email, exc);
			}
		}
		Exception retexc = null;
		Message msg = null;
		try {
			msg = createMessage(from, smsg, attachments, false);
			Transport.send(msg);
		} catch (Exception ex) {
			ex.printStackTrace();
			retexc = ex;
		}

		//if sent succesful, save outgoing message
		if (retexc == null && msg != null) {
			retexc = saveSent(msg, sentfolder);
			
		}
		return retexc;
		
	} //end sendMsg, SimpleMessage version

	public Exception sendMessage(SimpleMessage msg, String attnames[]) {
		UserProfile profile = environment.getProfile();
		String sender = profile.getEmailAddress();
		String name = profile.getDisplayName();
		String replyto = mprofile.getReplyTo();
		
		if (msg.getFrom() != null) {
			Identity from = msg.getFrom();
			sender = from.email;
			name = from.displayname;
			replyto = from.email;
		}
		
		msg.setReplyTo(replyto);
		
		if (name != null && name.length() > 0) {
			sender = name + " <" + sender + ">";
			
		}
		Exception retexc = null;
		ArrayList<Attachment> origattachments = getAttachments(msg.getId());
		ArrayList<Attachment> attachments = new ArrayList<Attachment>();
		for (String attname : attnames) {
			for (Attachment att : origattachments) {
				if (att.getFile().getName().equals(attname)) {
					attachments.add(att);
					break;
				}
			}
		}
		
		if (attachments.size() == 0 && msg.getAttachments() == null) {
			/*      b = mmgr.sendMsg(sender, msg.getReplyTo(),
			 breakAddr(msg.getTo()),
			 breakAddr(msg.getCc()), breakAddr(msg.getBcc()),
			 msg.getSubject(), msg.getContent(), msg.getMime());*/
			retexc = sendMsg(sender, msg, null);
		} else {
			
			retexc = sendMsg(sender, msg, attachments);
			
			clearAttachments(msg.getId());
		}

		/*    if (debug)
		 System.out.println("sendMail()" + b);*/
		return retexc;
	}
	
	public Message createMessage(String from, SimpleMessage smsg, ArrayList<Attachment> attachments, boolean tosave) throws Exception {
		MimeMessage msg = null;
		boolean success = true;
		
		String[] to = SimpleMessage.breakAddr(smsg.getTo());
		String[] cc = SimpleMessage.breakAddr(smsg.getCc());
		String[] bcc = SimpleMessage.breakAddr(smsg.getBcc());
		String replyTo = smsg.getReplyTo();
		
		msg = new MimeMessage(session);
		msg.setFrom(new InternetAddress(from));
		InternetAddress ia = null;

		//set the TO recipient
		for (int q = 0; q < to.length; q++) {
//        System.out.println("to["+q+"]="+to[q]);
			to[q] = to[q].replace(',', ' ');
			try {
				ia = getInternetAddress(to[q]);
			} catch (AddressException exc) {
				throw new AddressException(to[q]);
			}
			msg.addRecipient(Message.RecipientType.TO, ia);
		}

		//set the CC recipient
		for (int q = 0; q < cc.length; q++) {
			cc[q] = cc[q].replace(',', ' ');
			try {
				ia = getInternetAddress(cc[q]);
			} catch (AddressException exc) {
				throw new AddressException(cc[q]);
			}
			msg.addRecipient(Message.RecipientType.CC, ia);
		}

		//set BCC recipients
		for (int q = 0; q < bcc.length; q++) {
			bcc[q] = bcc[q].replace(',', ' ');
			try {
				ia = getInternetAddress(bcc[q]);
			} catch (AddressException exc) {
				throw new AddressException(bcc[q]);
			}
			msg.addRecipient(Message.RecipientType.BCC, ia);
		}

		//set reply to addr
		if (replyTo != null && replyTo.length() > 0) {
			Address[] replyaddr = new Address[1];
			replyaddr[0] = new InternetAddress(replyTo);
			msg.setReplyTo(replyaddr);
		}

		//add any header
		String headerLines[] = smsg.getHeaderLines();
		for (int i = 0; i < headerLines.length; ++i) {
			if (!headerLines[i].startsWith("Sonicle-reply-folder")) {
				msg.addHeaderLine(headerLines[i]);
			}
		}

		//add reply/references
		String inreplyto = smsg.getInReplyTo();
		String references[] = smsg.getReferences();
		String replyfolder = smsg.getReplyFolder();
		if (inreplyto != null) {
			msg.setHeader("In-Reply-To", inreplyto);
		}
		if (references != null && references[0] != null) {
			msg.setHeader("References", references[0]);
		}
		if (tosave) {
			if (replyfolder != null) {
				msg.setHeader("Sonicle-reply-folder", replyfolder);
			}
			msg.setHeader("Sonicle-draft", "true");
		}

		//add forward data
		String forwardedfrom = smsg.getForwardedFrom();
		String forwardedfolder = smsg.getForwardedFolder();
		if (forwardedfrom != null) {
			msg.setHeader("Forwarded-From", forwardedfrom);
		}
		if (tosave) {
			if (forwardedfolder != null) {
				msg.setHeader("Sonicle-forwarded-folder", forwardedfolder);
			}
			msg.setHeader("Sonicle-draft", "true");
		}
		//set the subject
		String subject = smsg.getSubject();
		try {
			//subject=MimeUtility.encodeText(smsg.getSubject(), "ISO-8859-1", null);
			subject = MimeUtility.encodeText(smsg.getSubject());
		} catch (Exception exc) {
		}
		msg.setSubject(subject);

		//set priority
		int priority = smsg.getPriority();
		if (priority != 3) {
			msg.setHeader("X-Priority", "" + priority);

			//set receipt
		}
		String receiptTo = from;
		try {
			receiptTo = MimeUtility.encodeText(from, "ISO-8859-1", null);
		} catch (Exception exc) {
		}
		if (smsg.getReceipt()) {
			msg.setHeader("Disposition-Notification-To", from);

			//see if there are any new attachments for the message
		}
		int noAttach;
		int newAttach;
		
		if (attachments == null) {
			newAttach = 0;
		} else {
			newAttach = attachments.size();
		}

		//get the array of the old attachments
		Part[] oldParts = smsg.getAttachments();

		//check if there are old attachments
		if (oldParts == null) {
			noAttach = 0;
		} else { //old attachments exist
			noAttach = oldParts.length;
		}
		
		if ((newAttach > 0) || (noAttach > 0) || !smsg.getMime().equalsIgnoreCase("text/plain")) {
			// create the main Multipart
			MimeMultipart mp = new MimeMultipart("mixed");
			
			String textcontent = smsg.getTextContent();
      //if is text, or no alternative text is available, add the content as one single body part
			//else create a multipart/alternative with both rich and text mime content
			if (textcontent == null || smsg.getMime().equalsIgnoreCase("text/plain")) {
				MimeBodyPart mbp1 = new MimeBodyPart();
				mbp1.setText(smsg.getContent(), "iso-8859-1");
				mbp1.setHeader("Content-type", smsg.getMime());
				
				mp.addBodyPart(mbp1);
			} else {
				MimeMultipart alternative = new MimeMultipart("alternative");
				//the rich part
				MimeBodyPart mbp2 = new MimeBodyPart();
				mbp2.setText(smsg.getContent(), "iso-8859-1");
				mbp2.setHeader("Content-type", smsg.getMime());
				//the text part
				MimeBodyPart mbp1 = new MimeBodyPart();

				/*          ByteArrayOutputStream bos=new ByteArrayOutputStream(textcontent.length());
				 com.sun.mail.util.QPEncoderStream qpe=new com.sun.mail.util.QPEncoderStream(bos);
				 for(int i=0;i<textcontent.length();++i) {
				 try {
				 qpe.write(textcontent.charAt(i));
				 } catch(IOException exc) {
				 exc.printStackTrace();
				 }
				 }
				 textcontent=new String(bos.toByteArray());*/
				mbp1.setText(textcontent, "iso-8859-1");
				mbp1.setHeader("Content-type", "text/plain");
//          mbp1.setHeader("Content-transfer-encoding","quoted-printable");

				alternative.addBodyPart(mbp1);
				alternative.addBodyPart(mbp2);
				
				MimeBodyPart altbody = new MimeBodyPart();
				altbody.setContent(alternative);
				
				mp.addBodyPart(altbody);
			}
			
			if (noAttach > 0) { //if there are old attachments
				// create the parts with the attachments
				//MimeBodyPart[] mbps2 = new MimeBodyPart[noAttach];
				//Part[] mbps2 = new Part[noAttach];

        //for(int e = 0;e < noAttach;e++) {
				//  mbps2[e] = (Part)oldParts[e];
				//}//end for e
				//add the old attachment parts
				for (int r = 0; r < noAttach; r++) {
					Object content = null;
					String contentType = null;
					String contentFileName = null;
					if (oldParts[r] instanceof Message) {
//                System.out.println("Attachment is a message");
						Message msgpart = (Message) oldParts[r];
						MimeMessage mm = new MimeMessage(session);
						mm.addFrom(msgpart.getFrom());
						mm.setRecipients(Message.RecipientType.TO, msgpart.getRecipients(Message.RecipientType.TO));
						mm.setRecipients(Message.RecipientType.CC, msgpart.getRecipients(Message.RecipientType.CC));
						mm.setRecipients(Message.RecipientType.BCC, msgpart.getRecipients(Message.RecipientType.BCC));
						mm.setReplyTo(msgpart.getReplyTo());
						mm.setSentDate(msgpart.getSentDate());
						mm.setSubject(msgpart.getSubject());
						mm.setContent(msgpart.getContent(), msgpart.getContentType());
						content = mm;
						contentType = "message/rfc822";
					} else {
//                System.out.println("Attachment is not a message");
						content = oldParts[r].getContent();
						contentType = oldParts[r].getContentType();
						contentFileName = oldParts[r].getFileName();
					}
					MimeBodyPart mbp = new MimeBodyPart();
					if (contentFileName != null) {
						mbp.setFileName(contentFileName);
//              System.out.println("adding attachment mime "+contentType+" filename "+contentFileName);
						contentType += "; name=\"" + contentFileName + "\"";
					}
					mbp.setDataHandler(new DataHandler(content, contentType));
					mp.addBodyPart(mbp);
				}
				
			} //end if, adding old attachments

			if (newAttach > 0) { //if there are new attachments
				// create the parts with the attachments
				MimeBodyPart[] mbps = new MimeBodyPart[newAttach];
				
				for (int e = 0; e < newAttach; e++) {
					mbps[e] = new MimeBodyPart();

					// attach the file to the message
					Attachment attach = (Attachment) attachments.get(e);
					FileDataSource fds = new FileDataSource(attach.getFile());
					mbps[e].setDataHandler(new DataHandler(fds));
          // filename starts has format:
					// "_" + userid + sessionId + "_" + filename
					//
					if (attach.isInline()) {
						mbps[e].setDisposition(Part.INLINE);
					}
					String contentFileName = attach.getName().trim();
					mbps[e].setFileName(contentFileName);
					String contentType = attach.getContentType() + "; name=\"" + contentFileName + "\"";
					mbps[e].setHeader("Content-type", contentType);
					if (attach.getCid() != null) {
						mbps[e].setHeader("Content-ID", "<" + attach.getCid() + ">");
						mbps[e].setHeader("X-Attachment-Id", attach.getCid());
						mbps[e].setDisposition(Part.INLINE);
					}
				} //end for e

				//add the new attachment parts
				for (int r = 0; r < newAttach; r++) {
					mp.addBodyPart(mbps[r]);
				}
				
			} //end if, adding new attachments

//
//          msg.addHeaderLine("This is a multi-part message in MIME format.");
			// add the Multipart to the message
			msg.setContent(mp);
			
		} else { //end if newattach
			msg.setText(smsg.getContent());
		} //singlepart message

		msg.setSentDate(new java.util.Date());
		
		return msg;
		
	}
	
	private Exception saveSent(Message msg, String sentfolder) {
		Exception retexc = null;
		try {
			checkCreateFolder(sentfolder);
			Folder outgoing = getFolder(sentfolder);
			msg.setFlag(Flags.Flag.SEEN, true);
			
			Message[] saveMsgs = new MimeMessage[1];
			saveMsgs[0] = msg;
			
			outgoing.appendMessages(saveMsgs);
		} catch (MessagingException exc) {
			exc.printStackTrace();
		}
		
		return retexc;
		
	}
	
	public Exception saveMessage(SimpleMessage msg, String attnames[], FolderCache fc) {
		Exception retexc = null;
		try {
			_saveMessage(msg, attnames, fc);
		} catch (Exception exc) {
			retexc = exc;
		}
		return retexc;
	}
	
	public Exception scheduleMessage(SimpleMessage msg, String attnames[], FolderCache fc, String senddate, String sendtime, String sendnotify) {
		Exception retexc = null;
		try {
			msg.addHeaderLine("Sonicle-send-scheduled: true");
			msg.addHeaderLine("Sonicle-send-date: " + senddate);
			msg.addHeaderLine("Sonicle-send-time: " + sendtime);
			msg.addHeaderLine("Sonicle-notify-delivery: " + sendnotify);
			Message m = _saveMessage(msg, attnames, fc);
			/*        String mid=m.getHeader("Message-ID")[0];
			 MailServiceGeneral mservgen=(MailServiceGeneral)wta.getServiceGeneralByName("mail");
        
			 UserProfile profile=wts.getEnvironment().getUserProfile();
			 String port=profile.getMailPort()+"";
			 String host=profile.getMailHost();
			 String protocol=profile.getMailProtocol();
			 String username=profile.getMailUsername();
			 String folderprefix=profile.getFolderPrefix();
			 String folderdrafts=profile.getFolderDrafts();
        
			 MailServiceGeneral.MailUser mu=mservgen.createMailUser(profile.getIDDomain(), host, protocol, port, username, folderprefix, folderdrafts);
			 mservgen.scheduleSendTask(mu, mid, senddate, sendtime, sendnotify);*/
		} catch (Exception exc) {
			retexc = exc;
		}
		return retexc;
	}
	
	private Message _saveMessage(SimpleMessage msg, String attnames[], FolderCache fc) throws Exception {
		UserProfile profile = environment.getProfile();
		String sender = profile.getEmailAddress();
		String name = profile.getDisplayName();
		String replyto = mprofile.getReplyTo();
		
		if (msg.getFrom() != null) {
			Identity from = msg.getFrom();
			sender = from.email;
			name = from.displayname;
			replyto = from.email;
		}
		
		msg.setReplyTo(replyto);
		
		if (name != null && name.length() > 0) {
			sender = name + " <" + sender + ">";
			
		}
		ArrayList<Attachment> origattachments = getAttachments(msg.getId());
		ArrayList<Attachment> attachments = new ArrayList<Attachment>();
		for (String attname : attnames) {
			for (Attachment att : origattachments) {
				if (att.getFile().getName().equals(attname)) {
					attachments.add(att);
					//System.out.println("Adding attachment : "+attname+" -> "+att.getName());
					break;
				}
			}
		}
		
		Message newmsg = createMessage(sender, msg, attachments, true);
		newmsg.setHeader("Sonicle-draft", "true");
    //FolderCache fc=getFolderCache(profile.getFolderDrafts());
		//newmsg.writeTo(new FileOutputStream("C:/Users/gbulfon/Desktop/TEST.eml"));
		fc.save(newmsg);
		clearAttachments(msg.getId());
		
		return newmsg;
	}
	
	private SimpleMessage getForwardMsg(int id, Message msg, boolean richContent, String fromtitle, String totitle, String cctitle, String datetitle, String subjecttitle, boolean attached) {
		Message forward = new MimeMessage(session);
		if (!attached) {
			try {
				StringBuffer htmlsb = new StringBuffer();
				StringBuffer textsb = new StringBuffer();
				boolean isHtml = appendReplyParts(msg, htmlsb, textsb, null);
    //      System.out.println("isHtml="+isHtml);
				//      System.out.println("richContent="+richContent);
				String html = "<HTML><BODY>" + htmlsb.toString() + "</BODY></HTML>";
				if (!richContent) {
					forward.setText(getForwardBody(msg, textsb.toString(), SimpleMessage.FORMAT_TEXT, false, fromtitle, totitle, cctitle, datetitle, subjecttitle));
				} else if (!isHtml) {
					forward.setText(getForwardBody(msg, textsb.toString(), SimpleMessage.FORMAT_PREFORMATTED, false, fromtitle, totitle, cctitle, datetitle, subjecttitle));
				} else {
					forward.setText(getForwardBody(msg, html, SimpleMessage.FORMAT_HTML, true, fromtitle, totitle, cctitle, datetitle, subjecttitle));
				}
			} catch (Exception exc) {
				exc.printStackTrace();
			}
		}
		
		try {
			String msgid = null;
			String vh[] = msg.getHeader("Message-ID");
			if (vh != null) {
				msgid = vh[0];
			}
			if (msgid != null) {
				forward.setHeader("Forwarded-From", msgid);
			}
		} catch (MessagingException exc) {
			exc.printStackTrace();
		}
		
		SimpleMessage fwd = new SimpleMessage(id, forward);
		fwd.setTo("");

    // Update appropriate subject
		// Fwd: subject
		try {
			String subject = msg.getSubject();
			if (subject == null) {
				subject = "";
			}
			if (!subject.toLowerCase().startsWith("fwd: ")) {
				fwd.setSubject("Fwd: " + subject);
			} else {
				fwd.setSubject(msg.getSubject());
			}
		} catch (MessagingException e) {
			e.printStackTrace();
//      System.out.println("*** SimpleMessage: " +e);
		}
		
		return fwd;
	}
	
	private String getForwardBody(Message msg, String body, int format, boolean isHtml, String fromtitle, String totitle, String cctitle, String datetitle, String subjecttitle) throws MessagingException {
		UserProfile profile = environment.getProfile();
		Locale locale = profile.getLocale();
		String msgSubject = msg.getSubject();
		if (msgSubject == null) {
			msgSubject = "";
		}
		msgSubject = OldUtils.htmlescape(msgSubject);
		Address ad[] = msg.getFrom();
		String msgFrom = "";
		if (ad != null) {
			msgFrom = getHTMLDecodedAddress(ad[0]);
		}
		java.util.Date dt = msg.getSentDate();
		String msgDate = "";
		if (dt != null) {
			msgDate = DateFormat.getDateTimeInstance(java.text.DateFormat.LONG, java.text.DateFormat.LONG, locale).format(dt);
		}
		ad = msg.getRecipients(Message.RecipientType.TO);
		String msgTo = null;
		if (ad != null) {
			msgTo = "";
			for (int j = 0; j < ad.length; ++j) {
				msgTo += getHTMLDecodedAddress(ad[j]) + " ";
			}
		}
		ad = msg.getRecipients(Message.RecipientType.CC);
		String msgCc = null;
		if (ad != null) {
			msgCc = "";
			for (int j = 0; j < ad.length; ++j) {
				msgCc += getHTMLDecodedAddress(ad[j]) + " ";
			}
		}
		
		StringBuffer sb = new StringBuffer();
		String cr = "\n";
		if (format != SimpleMessage.FORMAT_TEXT) {
			cr = "<BR>";
		}
		if (format != SimpleMessage.FORMAT_HTML) {
			if (format == SimpleMessage.FORMAT_PREFORMATTED) {
				sb.append("<TT>");
			}
			sb.append(cr + cr + cr + "----------------------------------------------------------------------------------" + cr + cr);
			sb.append(fromtitle + ": " + msgFrom + cr);
			if (msgTo != null) {
				sb.append(totitle + ": " + msgTo + cr);
			}
			if (msgCc != null) {
				sb.append(cctitle + ": " + msgCc + cr);
			}
			sb.append(datetitle + ": " + msgDate + cr);
			sb.append(subjecttitle + ": " + msgSubject + cr + cr);
			if (format == SimpleMessage.FORMAT_PREFORMATTED) {
				sb.append("</TT>");
			}
		} else {
			sb.append(cr + "<HR>" + cr + cr);
			sb.append("<font face='Arial, Helvetica, sans-serif' size=2>");
			sb.append("<B>" + fromtitle + ":</B> " + msgFrom + "<BR>");
			if (msgTo != null) {
				sb.append("<B>" + totitle + ":</B> " + msgTo + "<BR>");
			}
			if (msgCc != null) {
				sb.append("<B>" + cctitle + ":</B> " + msgCc + "<BR>");
			}
			sb.append("<B>" + datetitle + ":</B> " + msgDate + "<BR>");
			sb.append("<B>" + subjecttitle + ":</B> " + msgSubject + "<BR>");
			sb.append("</font><br>" + cr);
		}

    // Prepend "> " for each line in the body
		//
		if (body != null) {
			if (format == SimpleMessage.FORMAT_HTML) {
//        sb.append("<TABLE border=0 width='100%'><TR><td width=2 bgcolor=#000088></td><td width=2></td><td>");
//        sb.append("<BLOCKQUOTE style='BORDER-LEFT: #000080 2px solid; MARGIN-LEFT: 5px; PADDING-LEFT: 5px'>");
			}
			if (!isHtml) {
				if (format == SimpleMessage.FORMAT_PREFORMATTED) {
//          sb.append("<BLOCKQUOTE style='BORDER-LEFT: #000080 2px solid; MARGIN-LEFT: 5px; PADDING-LEFT: 5px'>");
					sb.append("<tt>");
				}
				StringTokenizer st = new StringTokenizer(body, "\n", true);
				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					if (token.equals("\n")) {
						sb.append(cr);
					} else {
						if (format == SimpleMessage.FORMAT_TEXT) {
							sb.append("> ");
						}
						sb.append(OldUtils.htmlescape(token));
					}
				}
				if (format == SimpleMessage.FORMAT_PREFORMATTED) {
					sb.append("</tt>");
//          sb.append("</BLOCKQUOTE>");
				}
			} else {
				int ix1 = body.indexOf("<BODY");
				if (ix1 >= 0) {
					int ix2 = body.indexOf(">", ix1 + 1);
					if (ix2 < 0) {
						ix2 = ix1 + 4;
					}
					int ix3 = body.indexOf("</BODY", ix2 + 1);
					if (ix3 > 0) {
						body = body.substring(ix2 + 1, ix3);
					} else {
						body = body.substring(ix2 + 1);
					}
				}
				sb.append(body);
			}
			if (format == SimpleMessage.FORMAT_HTML) {
//        sb.append("</td></tr></table>");
//        sb.append("</BLOCKQUOTE>");
			}
		}
		return sb.toString();
	}

	//Clone of MimeMessage that was private and used by my custom reply
	private Address[] eliminateDuplicates(Vector v, Address[] addrs) {
		if (addrs == null) {
			return null;
		}
		int gone = 0;
		for (int i = 0; i < addrs.length; i++) {
			boolean found = false;
			// search the vector for this address
			for (int j = 0; j < v.size(); j++) {
				if (((InternetAddress) v.elementAt(j)).equals(addrs[i])) {
					// found it; count it and remove it from the input array
					found = true;
					gone++;
					addrs[i] = null;
					break;
				}
			}
			if (!found) {
				v.addElement(addrs[i]);	// add new address to vector
			}
		}
		// if we found any duplicates, squish the array
		if (gone != 0) {
			Address[] a;
	    // new array should be same type as original array
			// XXX - there must be a better way, perhaps reflection?
			if (addrs instanceof InternetAddress[]) {
				a = new InternetAddress[addrs.length - gone];
			} else {
				a = new Address[addrs.length - gone];
			}
			for (int i = 0, j = 0; i < addrs.length; i++) {
				if (addrs[i] != null) {
					a[j++] = addrs[i];
				}
			}
			addrs = a;
		}
		return addrs;
	}

	//CLONE OF ImapMessage.reply() that does not set the ANSWERED Flag
	public Message reply(MimeMessage orig, boolean replyToAll) throws MessagingException {
		MimeMessage reply = new MimeMessage(session);
		/*
		 * Have to manipulate the raw Subject header so that we don't lose
		 * any encoding information.  This is safe because "Re:" isn't
		 * internationalized and (generally) isn't encoded.  If the entire
		 * Subject header is encoded, prefixing it with "Re: " still leaves
		 * a valid and correct encoded header.
		 */
		String subject = orig.getHeader("Subject", null);
		if (subject != null) {
			if (!subject.regionMatches(true, 0, "Re: ", 0, 4)) {
				subject = "Re: " + subject;
			}
			reply.setHeader("Subject", subject);
		}
		Address a[] = orig.getReplyTo();
		reply.setRecipients(Message.RecipientType.TO, a);
		if (replyToAll) {
			Vector v = new Vector();
			// add my own address to list
			InternetAddress me = InternetAddress.getLocalAddress(session);
			if (me != null) {
				v.addElement(me);
			}
			// add any alternate names I'm known by
			String alternates = null;
			if (session != null) {
				alternates = session.getProperty("mail.alternates");
			}
			if (alternates != null) {
				eliminateDuplicates(v,
						InternetAddress.parse(alternates, false));
			}
			// should we Cc all other original recipients?
			String replyallccStr = null;
			boolean replyallcc = false;
			if (session != null) {
				replyallcc = PropUtil.getBooleanSessionProperty(session,
						"mail.replyallcc", false);
			}
			// add the recipients from the To field so far
			eliminateDuplicates(v, a);
			a = orig.getRecipients(Message.RecipientType.TO);
			a = eliminateDuplicates(v, a);
			if (a != null && a.length > 0) {
				if (replyallcc) {
					reply.addRecipients(Message.RecipientType.CC, a);
				} else {
					reply.addRecipients(Message.RecipientType.TO, a);
				}
			}
			a = orig.getRecipients(Message.RecipientType.CC);
			a = eliminateDuplicates(v, a);
			if (a != null && a.length > 0) {
				reply.addRecipients(Message.RecipientType.CC, a);
			}
			// don't eliminate duplicate newsgroups
			a = orig.getRecipients(MimeMessage.RecipientType.NEWSGROUPS);
			if (a != null && a.length > 0) {
				reply.setRecipients(MimeMessage.RecipientType.NEWSGROUPS, a);
			}
		}
		
		String msgId = orig.getHeader("Message-Id", null);
		if (msgId != null) {
			reply.setHeader("In-Reply-To", msgId);
		}

		/*
		 * Set the References header as described in RFC 2822:
		 *
		 * The "References:" field will contain the contents of the parent's
		 * "References:" field (if any) followed by the contents of the parent's
		 * "Message-ID:" field (if any).  If the parent message does not contain
		 * a "References:" field but does have an "In-Reply-To:" field
		 * containing a single message identifier, then the "References:" field
		 * will contain the contents of the parent's "In-Reply-To:" field
		 * followed by the contents of the parent's "Message-ID:" field (if
		 * any).  If the parent has none of the "References:", "In-Reply-To:",
		 * or "Message-ID:" fields, then the new message will have no
		 * "References:" field.
		 */
		String refs = orig.getHeader("References", " ");
		if (refs == null) {
			// XXX - should only use if it contains a single message identifier
			refs = orig.getHeader("In-Reply-To", " ");
		}
		if (msgId != null) {
			if (refs != null) {
				refs = MimeUtility.unfold(refs) + " " + msgId;
			} else {
				refs = msgId;
			}
		}
		if (refs != null) {
			reply.setHeader("References", MimeUtility.fold(12, refs));
		}

	//try {
		//    setFlags(answeredFlag, true);
		//} catch (MessagingException mex) {
		//    // ignore it
		//}
		return reply;
	}

	// used above in reply()
	private static final Flags answeredFlag = new Flags(Flags.Flag.ANSWERED);
	
	private SimpleMessage getReplyMsg(int id, Message msg, boolean replyAll, boolean richContent, String myemail, boolean includeOriginal, String fromtitle, String totitle, String cctitle, String datetitle, String subjecttitle) {
		try {
			Message reply = reply((MimeMessage) msg, replyAll);
			
			removeDestination(reply, myemail);
			for (Identity ident : mprofile.getIdentities()) {
				removeDestination(reply, ident.email);
			}

      // Setup the message body
			//
			StringBuffer htmlsb = new StringBuffer();
			StringBuffer textsb = new StringBuffer();
			ArrayList<String> attnames = new ArrayList<String>();
			if (includeOriginal) {
				boolean isHtml = appendReplyParts(msg, htmlsb, textsb, attnames);
				String html = "<HTML><BODY>" + htmlsb.toString() + "</BODY></HTML>";
				String text = null;
				if (!richContent) {
					text = getReplyBody(msg, textsb.toString(), SimpleMessage.FORMAT_TEXT, false, fromtitle, totitle, cctitle, datetitle, subjecttitle, attnames);
				} else if (!isHtml) {
					text = getReplyBody(msg, textsb.toString(), SimpleMessage.FORMAT_PREFORMATTED, false, fromtitle, totitle, cctitle, datetitle, subjecttitle, attnames);
				} else {
					text = getReplyBody(msg, html, SimpleMessage.FORMAT_HTML, true, fromtitle, totitle, cctitle, datetitle, subjecttitle, attnames);
				}
				reply.setText(text);
			} else {
				reply.setText("");
			}
			return new SimpleMessage(id, reply);
		} catch (MessagingException e) {
			e.printStackTrace();
//      System.out.println("*** SimpleMessage: " + e);
			return null;
		} catch (IOException e) {
			e.printStackTrace();
//      System.out.println("*** SimpleMessage: " + e);
			return null;
		}
	}
	
	private String getReplyBody(Message msg, String body, int format, boolean isHtml, String fromtitle, String totitle, String cctitle, String datetitle, String subjecttitle, ArrayList<String> attnames) throws MessagingException {
		UserProfile profile = environment.getProfile();
		Locale locale = profile.getLocale();
		String msgSubject = msg.getSubject();
		if (msgSubject == null) {
			msgSubject = "";
		}
		msgSubject = OldUtils.htmlescape(msgSubject);
		Address ad[] = msg.getFrom();
		String msgFrom = "";
		if (ad != null) {
			msgFrom = getHTMLDecodedAddress(ad[0]);
		}
		java.util.Date dt = msg.getSentDate();
		String msgDate = "";
		if (dt != null) {
			msgDate = DateFormat.getDateTimeInstance(java.text.DateFormat.LONG, java.text.DateFormat.LONG, locale).format(dt);
		}
		ad = msg.getRecipients(Message.RecipientType.TO);
		String msgTo = null;
		if (ad != null) {
			msgTo = "";
			for (int j = 0; j < ad.length; ++j) {
				msgTo += getHTMLDecodedAddress(ad[j]) + " ";
			}
		}
		ad = msg.getRecipients(Message.RecipientType.CC);
		String msgCc = null;
		if (ad != null) {
			msgCc = "";
			for (int j = 0; j < ad.length; ++j) {
				msgCc += getHTMLDecodedAddress(ad[j]) + " ";
			}
		}

		//
		String sfrom = "";
		try {
			if (msg.getFrom() != null) {
				InternetAddress ia = (InternetAddress) msg.getFrom()[0];
				String personal = ia.getPersonal();
				String mail = ia.getAddress();
				if (personal == null || personal.equals(mail)) {
					sfrom = mail;
				} else {
					sfrom = personal + " <" + mail + ">";
				}
			}
		} catch (Exception exc) {
			
		}
		StringBuffer sb = new StringBuffer();
		String cr = "\n";
		if (format != SimpleMessage.FORMAT_TEXT) {
			cr = "<BR>";
		}
		if (format != SimpleMessage.FORMAT_HTML) {
			if (format == SimpleMessage.FORMAT_PREFORMATTED) {
				sb.append("<TT>");
			}
			sb.append(cr + cr + cr + "----------------------------------------------------------------------------------" + cr + cr);
			sb.append(fromtitle + ": " + msgFrom + cr);
			if (msgTo != null) {
				sb.append(totitle + ": " + msgTo + cr);
			}
			if (msgCc != null) {
				sb.append(cctitle + ": " + msgCc + cr);
			}
			sb.append(datetitle + ": " + msgDate + cr);
			sb.append(subjecttitle + ": " + msgSubject + cr + cr);
			if (format == SimpleMessage.FORMAT_PREFORMATTED) {
				sb.append("</TT>");
			}
		} else {
			sb.append(cr + "<HR>" + cr + cr);
			sb.append("<font face='Arial, Helvetica, sans-serif' size=2>");
			sb.append("<B>" + fromtitle + ":</B> " + msgFrom + "<BR>");
			if (msgTo != null) {
				sb.append("<B>" + totitle + ":</B> " + msgTo + "<BR>");
			}
			if (msgCc != null) {
				sb.append("<B>" + cctitle + ":</B> " + msgCc + "<BR>");
			}
			sb.append("<B>" + datetitle + ":</B> " + msgDate + "<BR>");
			sb.append("<B>" + subjecttitle + ":</B> " + msgSubject + "<BR>");
			sb.append("</font><br>" + cr);
		}

    // Prepend "> " for each line in the body
		//
		if (body != null) {
			if (format == SimpleMessage.FORMAT_HTML) {
//        sb.append("<TABLE border=0 width='100%'><TR><td width=2 bgcolor=#000088></td><td width=2></td><td>");
				sb.append("<BLOCKQUOTE style='BORDER-LEFT: #000080 2px solid; MARGIN-LEFT: 5px; PADDING-LEFT: 5px'>");
			}
			if (!isHtml) {
				if (format == SimpleMessage.FORMAT_PREFORMATTED) {
					sb.append("<BLOCKQUOTE style='BORDER-LEFT: #000080 2px solid; MARGIN-LEFT: 5px; PADDING-LEFT: 5px'>");
					sb.append("<tt>");
				}
				StringTokenizer st = new StringTokenizer(body, "\n", true);
				while (st.hasMoreTokens()) {
					String token = st.nextToken();
					if (token.equals("\n")) {
						sb.append(cr);
					} else {
						if (format == SimpleMessage.FORMAT_TEXT) {
							sb.append("> ");
						}
						sb.append(OldUtils.htmlescape(token));
					}
				}
				if (format == SimpleMessage.FORMAT_PREFORMATTED) {
					sb.append("</tt>");
					sb.append("</BLOCKQUOTE>");
				}
			} else {
				String ubody = body.toUpperCase();
				while (true) {
					int ix1 = ubody.indexOf("<BODY");
					if (ix1 < 0) {
						break;
					}
					int ix2 = ubody.indexOf(">", ix1 + 1);
					if (ix2 < 0) {
						ix2 = ix1 + 4;
					}
					int ix3 = ubody.indexOf("</BODY", ix2 + 1);
					if (ix3 > 0) {
						body = body.substring(ix2 + 1, ix3);
						ubody = ubody.substring(ix2 + 1, ix3);
					} else {
						body = body.substring(ix2 + 1);
						ubody = ubody.substring(ix2 + 1);
					}
				}
				//body=removeStartEndTag(body,unwantedTags);

				sb.append(body);
			}
			htmlAppendAttachmentNames(sb, attnames);
			if (format == SimpleMessage.FORMAT_HTML) {
//        sb.append("</td></tr></table>");
				sb.append("</BLOCKQUOTE>");
			}
		}
		return sb.toString();
	}
	
	private void removeDestination(Message msg, String email) throws MessagingException {
		Address a[] = null;
		try {
			a = msg.getRecipients(Message.RecipientType.TO);
		} catch (AddressException exc) {
			
		}
		if (a != null) {
			msg.setRecipients(
					Message.RecipientType.TO,
					removeDestination(a, email)
			);
		}
		try {
			a = msg.getRecipients(Message.RecipientType.CC);
		} catch (AddressException exc) {
			
		}
		if (a != null) {
			msg.setRecipients(
					Message.RecipientType.CC,
					removeDestination(a, email)
			);
		}
		try {
			a = msg.getRecipients(Message.RecipientType.BCC);
		} catch (AddressException exc) {
			
		}
		if (a != null) {
			msg.setRecipients(
					Message.RecipientType.BCC,
					removeDestination(a, email)
			);
		}
	}
	
	private Address[] removeDestination(Address a[], String email) throws MessagingException {
		email = email.toLowerCase();
		Vector v = new Vector();
		for (int i = 0; i < a.length; ++i) {
			InternetAddress ia = (InternetAddress) a[i];
			if (!ia.getAddress().toLowerCase().equals(email)) {
				v.addElement(a[i]);
			}
		}
		Address na[] = new Address[v.size()];
		v.copyInto(na);
		return na;
	}
	
	private void textAppendAttachmentNames(StringBuffer sb, ArrayList<String> attnames) {
		if (attnames.size() > 0) {
			sb.append("\n\n");
			for (String name : attnames) {
				sb.append("<<" + name + ">>\n");
			}
		}
	}
	
	private void htmlAppendAttachmentNames(StringBuffer sb, ArrayList<String> attnames) {
		if (attnames.size() > 0) {
			sb.append("<br><br>");
			for (String name : attnames) {
				sb.append("&lt;&lt;" + name + "&gt;&gt;<br>");
			}
		}
	}
	
	private String getTextPlainContentAsString(Part p) throws IOException, MessagingException {
		java.io.InputStream istream = null;
		String charset = OldUtils.getCharset(p.getContentType());
		if (!java.nio.charset.Charset.isSupported(charset)) {
			charset = "ISO-8859-1";
		}
		try {
			if (p instanceof javax.mail.internet.MimeMessage) {
				javax.mail.internet.MimeMessage mm = (javax.mail.internet.MimeMessage) p;
				istream = mm.getInputStream();
			} else if (p instanceof javax.mail.internet.MimeBodyPart) {
				javax.mail.internet.MimeBodyPart mm = (javax.mail.internet.MimeBodyPart) p;
				istream = mm.getInputStream();
			}
		} catch (Exception exc) { //unhandled format, get Raw data
			if (p instanceof javax.mail.internet.MimeMessage) {
				javax.mail.internet.MimeMessage mm = (javax.mail.internet.MimeMessage) p;
				istream = mm.getRawInputStream();
			} else if (p instanceof javax.mail.internet.MimeBodyPart) {
				javax.mail.internet.MimeBodyPart mm = (javax.mail.internet.MimeBodyPart) p;
				istream = mm.getRawInputStream();
			}
		}
		
		if (istream == null) {
			throw new IOException("Unknown message class " + p.getClass().getName());
		}
		
		java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(istream, charset));
		String line = null;
		StringBuffer sb = new StringBuffer();
		while ((line = br.readLine()) != null) {
			sb.append(line);
			sb.append("\n");
		}
		br.close();
		return sb.toString();
	}
	
	private boolean appendReplyParts(Part p, StringBuffer htmlsb, StringBuffer textsb, ArrayList<String> attnames) throws MessagingException,
			IOException {
		boolean isHtml = false;
		String disp = p.getDisposition();
		if (disp != null && disp.equalsIgnoreCase(Part.ATTACHMENT)) {
			return false;
		}
		if (p.isMimeType("text/html")) {
			textsb.append(OldUtils.htmlToText(OldUtils.htmlunescapesource((String) p.getContent())));
			htmlsb.append(OldUtils.htmlescapefixsource((String) p.getContent()));
			isHtml = true;
		} else if (p.isMimeType("text/plain")) {
			String content = getTextPlainContentAsString(p);
			textsb.append(content);
			htmlsb.append(startpre + OldUtils.htmlescape(content) + endpre);
			isHtml = false;
		} else if (p.isMimeType("message/delivery-status") || p.isMimeType("message/disposition-notification")) {
			InputStream is = (InputStream) p.getContent();
			char cbuf[] = new char[8000];
			byte bbuf[] = new byte[8000];
			int n = 0;
			htmlsb.append(startpre);
			while ((n = is.read(bbuf)) >= 0) {
				if (n > 0) {
					for (int i = 0; i < n; ++i) {
						cbuf[i] = (char) bbuf[i];
					}
					textsb.append(cbuf);
					htmlsb.append(OldUtils.htmlescape(new String(cbuf)));
				}
			}
			htmlsb.append(endpre);
			is.close();
			isHtml = false;
		} else if (p.isMimeType("multipart/alternative")) {
			Multipart mp = (Multipart) p.getContent();
			Part bestPart = null;
			for (int i = 0; i < mp.getCount(); ++i) {
				Part part = mp.getBodyPart(i);
				if (part.isMimeType("multipart/*")) {
					isHtml = appendReplyParts(part, htmlsb, textsb, attnames);
					if (isHtml) {
						bestPart = null;
						break;
					}
				} else if (part.isMimeType("text/html")) {
					bestPart = part;
					break;
				} else if (bestPart == null && part.isMimeType("text/plain")) {
					bestPart = part;
				} else if (bestPart == null && part.isMimeType("message/*")) {
					bestPart = part;
				}
			}
			if (bestPart != null) {
				isHtml = appendReplyParts(bestPart, htmlsb, textsb, attnames);
			}
		} else if (p.isMimeType("multipart/*")) {
			Multipart mp = (Multipart) p.getContent();
			for (int i = 0; i < mp.getCount(); ++i) {
				if (appendReplyParts(mp.getBodyPart(i), htmlsb, textsb, attnames)) {
					isHtml = true;
				}
			}
		} else if (p.isMimeType("message/*")) {
			Object content = p.getContent();
			if (appendReplyParts((MimeMessage) content, htmlsb, textsb, attnames)) {
				isHtml = true;
			}
		} else {
			String filename = p.getFileName();
			String id[] = p.getHeader("Content-ID");
			if (id != null || filename != null) {
				if (id != null) {
					filename = id[0];
				}
				if (filename.startsWith("<")) {
					filename = filename.substring(1);
				}
				if (filename.endsWith(">")) {
					filename = filename.substring(0, filename.length() - 1);
				}
			}
			if (filename != null && attnames != null) {
				attnames.add(filename);
			}
		}
		textsb.append('\n');
		textsb.append('\n');
		
		return isHtml;
	}
	
	protected void finalize() {
		cleanup();
	}
	
	public void cleanup() {
		if (mft != null) {
			mft.abort();
		}
		if (ast != null && ast.isRunning()) {
			ast.cancel();
		}
		
		try {
			disconnect();
		} catch (Exception e) {
			e.printStackTrace();
		}
		clearAllAttachments();
		if (fcRoot != null) {
			fcRoot.cleanup();
		}
		fcRoot = null;
		for (FolderCache fc : foldersCache.values()) {
			fc.cleanup();
		}
		foldersCache.clear();
		validated = false;
	}
	
	protected void clearAllAttachments() {
		for (Integer id : msgattach.keySet()) {
			clearAttachments(id.intValue());
		}
		msgattach.clear();
	}

  // TODO: updateServerEvents!!!!!!!
/*    public void updateServerEvents() {
	 try {
	 ArrayList<ServerEvent> events=null;
	 synchronized(getMailFoldersThread()) {
	 Set<Entry<String, FolderCache>> entries=getFolderCacheEntries();
	 boolean first=true;
	 for(Entry<String, FolderCache> entry: entries) {
	 FolderCache mc=entry.getValue();
	 if (mc.unreadChanged()||mc.recentChanged()) {
	 long millis=System.currentTimeMillis();
	 if (events==null) events=new ArrayList<ServerEvent>();
	 mc.resetUnreadChanged();
	 mc.resetRecentChanged();
	 String fname=mc.getFolderName();
	 int unread=mc.getUnreadMessagesCount();
	 int recent=mc.getRecentMessagesCount();
	 boolean hasUnread=false;
	 boolean log=false;
	 if (!mc.isInbox()) {
	 hasUnread=mc.hasUnreadChildren();
	 if (recent>0) log=true;
	 }
	 String data="[ {id:'"+Utils.jsEscape(fname)+"',unread:"+unread+",recent:"+recent+",hasunread:"+hasUnread+",millis:"+millis+"} ]";
	 events.add(
	 new ServerEvent(
	 "recents",
	 "Email",
	 "Cartella: "+getInternationalFolderPath(mc),
	 data,log)
	 );
	 first=false;
	 }
	 }
	 }
	 addServerEvents(events);
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 }
	 }*/
	private String getMessageID(Message m) throws MessagingException {
		String ids[] = m.getHeader("Message-ID");
		if (ids == null) {
			return null;
		}
		return ids[0];
	}
	
	private int[] toInts(String values[]) {
		int ret[] = new int[values.length];
		for (int i = 0; i < values.length; ++i) {
			ret[i] = Integer.parseInt(values[i]);
		}
		return ret;
	}
	
	public boolean isSentFolder(String fullname) {
		String lastname = getLastFolderName(fullname);
		String plastname = getLastFolderName(mprofile.getFolderSent());
		if (lastname.equals(plastname)) {
			return true;
		}
		return false;
	}
	
	public boolean isTrashFolder(String fullname) {
		String lastname = getLastFolderName(fullname);
		String plastname = getLastFolderName(mprofile.getFolderTrash());
		if (lastname.equals(plastname)) {
			return true;
		}
		return false;
	}
	
	public boolean isDraftsFolder(String fullname) {
		String lastname = getLastFolderName(fullname);
		String plastname = getLastFolderName(mprofile.getFolderDrafts());
		if (lastname.equals(plastname)) {
			return true;
		}
		return false;
	}
	
	public boolean isSpamFolder(String fullname) {
		String lastname = getLastFolderName(fullname);
		String plastname = getLastFolderName(mprofile.getFolderSpam());
		if (lastname.equals(plastname)) {
			return true;
		}
		return false;
	}
	
	public boolean isSpecialFolder(String foldername) {
		boolean retval = isTrashFolder(foldername)
				|| isDraftsFolder(foldername)
				|| isInboxFolder(foldername)
				|| isSentFolder(foldername)
				|| isSpamFolder(foldername);
		return retval;
	}
	
	public boolean isInboxFolder(String foldername) {
		if (foldername.equals("INBOX")) {
			return true;
		}
		return false;
	}
	
	public boolean isUnderSharedFolder(String foldername) {
		boolean b = false;
		String str = null;
		for (String fn : sharedPrefixes) {
			str = fn + folderSeparator;
			if (foldername.startsWith(str)) {
				b = true;
				break;
			}
		}
		return b;
	}
	
	public boolean isSharedFolder(String foldername) {
		boolean b = false;
		for (String fn : sharedPrefixes) {
			if (foldername.equals(fn)) {
				b = true;
				break;
			}
		}
		return b;
	}
	
	public boolean isDocMgtFolder(String foldername) {
		UserProfile profile = environment.getProfile();
		if (!profile.hasDocumentManagement()) {
			return false;
		}
		boolean b = false;
		String df = profile.getDocumentManagementFolder();
		if (df != null && df.trim().length() > 0) {
			String lfn = getLastFolderName(foldername);
			String dfn = getLastFolderName(df);
			if (lfn.equals(dfn)) {
				b = true;
			}
		}
		return b;
	}
	
	public String getDecodedAddress(Address a) {
		String ret = "";
		try {
			InternetAddress ia = (InternetAddress) a;
			String personal = ia.getPersonal();
			String email = ia.getAddress();
			if (personal == null || personal.equals(email)) {
				ret = email;
			} else {
				ret = personal + " <" + email + ">";
			}
		} catch (RuntimeException exc) {
			ret = a.toString();
		}
		return ret;
	}
	
	public String getHTMLDecodedAddress(Address a) {
		String s = getDecodedAddress(a);
		return OldUtils.htmlescape(s);
	}
	
	public String getInternationalFolderName(FolderCache fc) {
		Folder folder = fc.getFolder();
		String desc = folder.getName();
		String fullname = folder.getFullName();
		//WebTopApp webtopapp=environment.getWebTopApp();
		Locale locale = environment.getProfile().getLocale();
		if (fc.isSharedFolder()) {
			desc = lookupResource(MailLocaleKey.FOLDERS_SHARED);
		} else if (fc.isInbox()) {
			desc = lookupResource(MailLocaleKey.FOLDERS_INBOX);
		} else if (fc.isDrafts()) {
			desc = lookupResource(MailLocaleKey.FOLDERS_DRAFTS);
		} else if (fc.isTrash()) {
			desc = lookupResource(MailLocaleKey.FOLDERS_TRASH);
		} else if (fc.isSent()) {
			desc = lookupResource(MailLocaleKey.FOLDERS_SENT);
		} else if (fc.isSpam()) {
			desc = lookupResource(MailLocaleKey.FOLDERS_SPAM);
		}
		return desc;
	}
	
	public String getInternationalFolderPath(FolderCache fc) throws MessagingException {
		String intpath = getInternationalFolderName(fc);
		char sep = folderSeparator;
		FolderCache parent = fc.getParent();
		while (parent != null && parent.isRoot()) {
			String intparent = getInternationalFolderName(parent);
			intpath = intparent + sep + intpath;
			parent = parent.getParent();
		}
		return intpath;
	}
	
	protected Folder getDefaultFolder() throws MessagingException {
		return store.getDefaultFolder();
	}
	
	protected Folder getFolder(String foldername) throws MessagingException {
		return store.getFolder(foldername);
	}
	
	private void checkCreateFolder(String foldername) throws MessagingException {
		Folder folder = store.getFolder(foldername);
		if (!folder.exists()) {
			folder.create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS);
		}
	}
	
	public static int getPriority(Message m) throws MessagingException {
		String xprio = null;
		String h[] = m.getHeader("X-Priority");
		if (h != null && h.length > 0) {
			xprio = h[0];
		}
		int priority = 3;
		if (xprio != null) {
			int ixp = xprio.indexOf(' ');
			if (ixp > 0) {
				xprio = xprio.substring(0, ixp);
			}
			try {
				priority = Integer.parseInt(xprio);
			} catch (RuntimeException exc) {
			}
		}
		return priority;
	}
	
	public boolean isInlineableMime(String contenttype) {
		return inlineableMimes.contains(contenttype.toLowerCase());
	}
	
	public synchronized int getNewMessageID() {
		return ++newMessageID;
	}
	
	public ArrayList<Attachment> getAttachments(int msgid) {
		ArrayList<Attachment> attachments = msgattach.get(new Integer(msgid));
		if (attachments != null) {
			return attachments;
		}
		return emptyAttachments;
	}
	
	public ArrayList<Attachment> getCloudAttachments(int msgid) {
		ArrayList<Attachment> attachments = msgcloudattach.get(new Integer(msgid));
		if (attachments != null) {
			return attachments;
		}
		return emptyAttachments;
	}
	
	public Attachment getAttachment(int msgid, String tempName) {
		ArrayList<Attachment> attachments = getAttachments(msgid);
		for (Attachment att : attachments) {
			if (att.getFile().getName().equals(tempName)) {
				return att;
			}
		}
		return null;
	}
	
	public Attachment getCloudAttachment(int msgid, String filename) {
		ArrayList<Attachment> attachments = getCloudAttachments(msgid);
		for (Attachment att : attachments) {
			if (att.getName().equals(filename)) {
				return att;
			}
		}
		return null;
	}
	
	public Attachment getAttachmentByCid(int msgid, String cid) {
		ArrayList<Attachment> attachments = getAttachments(msgid);
		for (Attachment att : attachments) {
			if (att.getCid().equals(cid)) {
				return att;
			}
		}
		return null;
	}
	
	public void clearAttachments(int msgid) {
		ArrayList<Attachment> attachments = getAttachments(msgid);
		for (Attachment a : attachments) {
			a.getFile().delete();
		}
		attachments.clear();

	// TODO: remove cloud attachements
/*    attachments=getCloudAttachments(msgid);
		 for(Attachment a: attachments) {
		 try {
		 VFSService vfs=(VFSService)wts.getServiceByName("vfs");
		 vfs.discard(a.getVFSUri());
		 } catch(Exception exc) {
		 exc.printStackTrace();
		 }
		 }
		 attachments.clear();*/
	}
	
	public void putAttachments(int msgid, ArrayList<Attachment> attachments) {
		msgattach.put(new Integer(msgid), attachments);
	}
	
	public void putCloudAttachments(int msgid, ArrayList<Attachment> attachments) {
		msgcloudattach.put(new Integer(msgid), attachments);
	}
	
	public Attachment attachFile(int msgid, File file, String name, String contentType, String cid, boolean inline) {
		Attachment attachment = null;
		ArrayList<Attachment> attachments = getAttachments(msgid);
		if (attachments == null || attachments == emptyAttachments) {
			attachments = new ArrayList<Attachment>();
			putAttachments(msgid, attachments);
		}
		attachment = new Attachment(file, name, contentType, cid, inline);
		attachments.add(attachment);
		return attachment;
	}
	
	public Attachment attachCloud(int msgid, FileObject fileObject, String name, String contentType, String vfsuri) {
		Attachment attachment = null;
		ArrayList<Attachment> attachments = getCloudAttachments(msgid);
		if (attachments == null || attachments == emptyAttachments) {
			attachments = new ArrayList<Attachment>();
			putCloudAttachments(msgid, attachments);
		}
		attachment = new Attachment(fileObject, name, contentType, vfsuri);
		attachments.add(attachment);
		return attachment;
	}
	
	public String replaceCidUrls(String html, HTMLMailData maildata, String preurl) throws MessagingException {
		for (String cidname : maildata.getCidNames()) {
			//Part part=maildata.getCidPart(cidname);
			String surl = preurl + cidname;
			html = OldUtils.replace(html, "cid:" + cidname, surl, false);
		}
		return html;
	}
	
	public String removeWrongBase(String html) {
		String lhtml = html.toLowerCase();
		int ix = lhtml.indexOf("<base");
		if (ix >= 0) {
			int iy = lhtml.indexOf(">", ix);
			if (iy >= 0) {
				String base = lhtml.substring(ix, iy);
				if (base.indexOf("file:") >= 0) {
					String html1 = html.substring(0, ix);
					String html2 = html.substring(iy + 1);
					html = html1 + html2;
				}
			}
		}
		return html;
	}
	
	private FolderCache createFolderCache(Folder f) throws MessagingException {
		FolderCache fc = new FolderCache(f, this, environment);
		String fname = fc.getFolderName();
		foldersCache.put(fname, fc);
		return fc;
	}
	
	private void loadFoldersCache() throws MessagingException {
		Folder f = getDefaultFolder();
		fcRoot = createFolderCache(f);
		fcRoot.setIsRoot(true);
		Folder children[] = fcRoot.getFolder().list();
		final ArrayList<FolderCache> rootParents = new ArrayList<FolderCache>();
		for (Folder child : children) {
			FolderCache fcc = addSingleFoldersCache(fcRoot, child);
			if (!fcc.isStartupLeaf()) {
				rootParents.add(fcc);
			}
		}
		
		Thread engine = new Thread(
				new Runnable() {
					public void run() {
						synchronized (mft) {
							try {
								for (FolderCache fc : rootParents) {
									_loadFoldersCache(fc);
								}
							} catch (MessagingException exc) {
								exc.printStackTrace();
							}
						}
					}
				}
		);
		engine.start();
	}
	
	private void _loadFoldersCache(FolderCache fc) throws MessagingException {
		Folder f = fc.getFolder();
		Folder children[] = f.list();
		for (Folder child : children) {
			FolderCache fcc = addFoldersCache(fc, child);
		}
		//If shared folders, check for same descriptions and in case add login
		if (isSharedFolder(f.getFullName()) && fc.hasChildren()) {
			HashMap<String, ArrayList<FolderCache>> hm = new HashMap<String, ArrayList<FolderCache>>();
			//map descriptions to list of folders
			for (FolderCache child : fc.getChildren()) {
				String desc = child.getDescription();
				ArrayList<FolderCache> al = hm.get(desc);
				if (al == null) {
					al = new ArrayList<FolderCache>();
					al.add(child);
					hm.put(desc, al);
				} else {
					al.add(child);
				}
			}
			//for folders with list>1 change description to all elements
			for (ArrayList<FolderCache> al : hm.values()) {
				if (al.size() > 1) {
					for (FolderCache fcc : al) {
						com.sonicle.security.Principal sip = fcc.getSharedInboxPrincipal();
						String user = sip.getUserId();
						fcc.setWebTopUser(user);
						fcc.setDescription(fcc.getDescription() + " [" + user + "]");
					}
				}
			}
		}
	}
	
	private FolderCache addSingleFoldersCache(FolderCache parent, Folder child) throws MessagingException {
		//logger.debug("adding {} to {}",child.getName(),parent.getFolderName());
		String cname = child.getFullName();
		FolderCache fcChild = createFolderCache(child);
		fcChild.setParent(parent);
		parent.addChild(fcChild);
		fcChild.setStartupLeaf(isLeaf((IMAPFolder) child));
		
		return fcChild;
	}
	
	private boolean isLeaf(IMAPFolder folder) throws MessagingException {
		String atts[] = folder.getAttributes();
		boolean leaf = true;
		boolean noinferiors = false;
		for (String att : atts) {
			if (att.equals("\\HasChildren")) {
				leaf = false;
			} else if (att.equals("\\Noinferiors")) {
				noinferiors = true;
			}
		}
		if (noinferiors) {
			leaf = true;
		}
		return leaf;
	}
	
	protected FolderCache addFoldersCache(FolderCache parent, Folder child) throws MessagingException {
		//logger.debug("adding {} to {}",child.getName(),parent.getFolderName());
		FolderCache fcChild = addSingleFoldersCache(parent, child);
		boolean leaf = fcChild.isStartupLeaf();
		if (!leaf) {
			_loadFoldersCache(fcChild);
		}
		return fcChild;
	}
	
	private void destroyFolderCache(FolderCache fc) {
		ArrayList<FolderCache> fcc = fc.getChildren();
		if (fcc != null) {
			FolderCache afc[] = null;
			int len = fcc.size();
			if (len > 0) {
				afc = new FolderCache[len];
				fc.getChildren().toArray(afc);
				for (FolderCache child : afc) {
					destroyFolderCache(child);
				}
			}
		}
		FolderCache fcp = fc.getParent();
		if (fcp != null) {
			fcp.removeChild(fc);
		}
		fc.cleanup();
		try {
			fc.close();
		} catch (Exception exc) {
		}
	}
	
	private ArrayList<FolderCache> opened = new ArrayList<FolderCache>();

	protected void poolOpened(FolderCache fc) {
		if (opened.size() >= 5) {
			FolderCache rfc = opened.remove(0);
			rfc.cleanup();
			rfc.close();
			rfc.setForceRefresh();
		}
		opened.add(fc);
	}

	//Client service requests
	public void processGetImapTree(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String pfoldername = request.getParameter("node");
		out.print("[");
		Folder folder = null;
		try {
			checkStoreConnected();
			if (pfoldername.equals("root")) {
				folder = getDefaultFolder();
			} else {
				folder = getFolder(pfoldername);
			}
			Folder folders[] = folder.list();
			String fprefix = mprofile.getFolderPrefix();
			boolean level1 = (fprefix != null && fprefix.equals("INBOX."));
			outputFolders(folder, folders, level1, out);
			out.println("]");
			
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	Comparator<Folder> ucomp = new Comparator<Folder>() {
		
		public int compare(Folder f1, Folder f2) {
			int ret = 0;
			try {
				FolderCache fc1 = getFolderCache(f1.getFullName());
				FolderCache fc2 = getFolderCache(f2.getFullName());
				String u1 = fc1.getWebTopUser();
				String u2 = fc2.getWebTopUser();
				ret = u1.compareTo(u2);
			} catch (MessagingException exc) {
				exc.printStackTrace();
			}
			return ret;
		}
		
	};
	
	Comparator<Folder> dcomp = new Comparator<Folder>() {
		
		public int compare(Folder f1, Folder f2) {
			int ret = 0;
			try {
				FolderCache fc1 = getFolderCache(f1.getFullName());
				FolderCache fc2 = getFolderCache(f2.getFullName());
				String desc1 = fc1.getDescription();
				String desc2 = fc2.getDescription();
				ret = desc1.compareTo(desc2);
			} catch (MessagingException exc) {
				exc.printStackTrace();
			}
			return ret;
		}
		
	};
	
	private void outputFolders(Folder parent, Folder folders[], boolean level1, PrintWriter out) throws Exception {
		ArrayList<Folder> afolders = sortFolders(folders);
		//If Shared Folders, sort on description
		if (isSharedFolder(parent.getFullName())) {
			String ss = mprofile.getSharedSort();
			if (ss.equals("D")) {
				Collections.sort(afolders, dcomp);
			} else if (ss.equals("U")) {
				Collections.sort(afolders, ucomp);
			}
		}
		
		for (Folder f : afolders) {
			String foldername = f.getFullName();
			FolderCache mc = getFolderCache(foldername);
			if (mc == null) {
				//logger.debug("outputFolders mc is null: foldername={}",foldername);
				continue;
			}
			//String shortfoldername=getShortFolderName(foldername);
			IMAPFolder imapf = (IMAPFolder) f;
			String atts[] = imapf.getAttributes();
			boolean leaf = true;
			boolean noinferiors = false;
			for (String att : atts) {
				if (att.equals("\\HasChildren")) {
					if (!level1 || !foldername.equals("INBOX")) {
						leaf = false;
					}
				} else if (att.equals("\\Noinferiors")) {
					noinferiors = true;
				}
			}
			if (noinferiors) {
				leaf = true;
			}
			//boolean leaf=isLeaf((IMAPFolder)f);
			//logger.debug("folder {} isleaf={}, level1={}",f.getFullName(),leaf,level1);
			//if (leaf) {
			//	if (!level1 || !foldername.equals("INBOX")) leaf=false;
			//}
			
			String iconCls = "wtmail-icon-imap-folder";
			int unread = 0;
			boolean hasUnread = false;
			boolean nounread = false;
			if (mc.isSharedFolder()) {
				iconCls = "wtmail-icon-shared-folder";
				nounread = true;
			} else if (mc.isInbox()) {
				iconCls = "wtmail-icon-inbox-folder";
			} else if (mc.isSent()) {
				iconCls = "wtmail-icon-sent-folder";
				nounread = true;
			} else if (mc.isDrafts()) {
				iconCls = "wtmail-icon-drafts-folder";
				nounread = true;
			} else if (mc.isTrash()) {
				iconCls = "wtmail-icon-trash-folder";
				nounread = true;
			} else if (mc.isSpam()) {
				iconCls = "wtmail-icon-spam-folder";
				nounread = true;
			} else if (mc.isDocMgt()) {
				iconCls = "wtmail-icon-docmgt-folder";
			} else if (mc.isSharedInbox()) {
				iconCls = "wtmail-icon-inbox-folder";
			}
			if (!nounread) {
				unread = mc.getUnreadMessagesCount();
				hasUnread = mc.hasUnreadChildren();
			}
			String text = mc.getDescription();
			String ss = "{id:'" + OldUtils.jsEscape(foldername)
					+ "',text:'" + OldUtils.jsEscape(text)
					+ "',folder:'" + OldUtils.jsEscape(text)
					+ "',leaf:" + leaf
					+ ",iconCls: '" + iconCls
					+ "',unread:" + unread
					+ ",hasUnread:" + hasUnread;
			if (mc.isInbox()) {
				ss += ",isInbox: true";
			}
			if (mc.isSent()) {
				ss += ",isSent: true";
			}
			if (mc.isDrafts()) {
				ss += ",isDrafts: true";
			}
			if (mc.isTrash()) {
				ss += ",isTrash: true";
			}
			if (mc.isSpam()) {
				ss += ",isSpam: true";
			}
			if (mc.isScanForcedOff()) {
				ss += ", scanOff: true";
			} else if (mc.isScanForcedOn()) {
				ss += ", scanOn: true";
			} else if (mc.isScanEnabled()) {
				ss += ", scanEnabled: true";
			}
			ss += "},";
			out.print(ss);
			if (level1 && foldername.equals("INBOX")) {
				outputFolders(f, f.list(), false, out);
			}
		}
	}
	
	private ArrayList<Folder> sortFolders(Folder folders[]) {
		ArrayList<Folder> afolders = new ArrayList<Folder>();
		//add all non special fo the array and map special ones for later insert
		Folder inbox = null;
		Folder sent = null;
		Folder drafts = null;
		Folder trash = null;
		Folder spam = null;
		for (Folder f : folders) {
			String foldername = f.getFullName();
			String shortfoldername = getShortFolderName(foldername);
			if (isInboxFolder(shortfoldername)) {
				inbox = f;
			} else if (isSentFolder(shortfoldername)) {
				sent = f;
			} else if (isDraftsFolder(shortfoldername)) {
				drafts = f;
			} else if (isTrashFolder(shortfoldername)) {
				trash = f;
			} else if (isSpamFolder(shortfoldername)) {
				spam = f;
			} else {
				afolders.add(f);
			}
		}
		//add any mapped special folder in order on top
		if (trash != null) {
			afolders.add(0, trash);
		}
		if (spam != null) {
			afolders.add(0, spam);
		}
		if (sent != null) {
			afolders.add(0, sent);
		}
		if (drafts != null) {
			afolders.add(0, drafts);
		}
		if (inbox != null) {
			afolders.add(0, inbox);
		}
		return afolders;
	}
	
	public void processGetDocMgtTree(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		//WebTopApp wta=environment.getWebTopApp();
		UserProfile profile = environment.getProfile();
		String pnodename = request.getParameter("node");
		String ss = "[\n";
		Connection con = null;
		Statement stmt = null;
		ResultSet rs = null;
		try {
			checkStoreConnected();
			con = getConnection();
			stmt = con.createStatement();
			if (pnodename.equals("docmgtroot")) {
				rs = stmt.executeQuery(
						"select docmgt_associations.idcategory as idcategory,docmgt_categories.description as description from "
						+ "docmgt_associations,docmgt_categories where "
						+ "docmgt_associations.idcategory=docmgt_categories.idcategory and "
						+ "login='" + profile.getUserId() + "' order by docmgt_categories.description"
				);
				boolean first = true;
				while (rs.next()) {
					if (!first) {
						ss += ",\n";
					}
					String idcategory = rs.getString("idcategory");
					String description = rs.getString("description");
					ss += "{id:'" + OldUtils.jsEscape(idcategory)
							+ "',text:'" + OldUtils.jsEscape(description)
							+ "',leaf:false,iconCls: 'iconImapFolder'}";
					first = false;
				}
			} else {
				String idcategory = pnodename;
				rs = stmt.executeQuery(
						"select idsubcategory, description from docmgt_subcategories where idcategory='" + idcategory + "' order by description"
				);
				boolean first = true;
				while (rs.next()) {
					if (!first) {
						ss += ",\n";
					}
					String idsubcategory = rs.getString("idsubcategory");
					String description = rs.getString("description");
					ss += "{id:'" + OldUtils.jsEscape(idcategory) + "|" + OldUtils.jsEscape(idsubcategory)
							+ "',text:'" + OldUtils.jsEscape(description)
							+ "',leaf:true,iconCls: 'iconImapFolder'}";
					first = false;
				}
			}
			ss += "\n]";
			out.println(ss);
		} catch (Exception exc) {
			exc.printStackTrace();
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (Exception exc) {
				}
			}
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
	}
	
	public void processMoveMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String tofolder = request.getParameter("tofolder");
		String allfiltered = request.getParameter("allfiltered");
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String ids[] = null;
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			FolderCache tomcache = getFolderCache(tofolder);
			if (allfiltered == null) {
				ids = request.getParameterValues("ids");
				if (!mf) {
					moveMessages(mcache, tomcache, toInts(ids));
				} else {
					int iids[] = new int[1];
					for (String id : ids) {
						int ix = id.indexOf("|");
						fromfolder = id.substring(0, ix);
						id = id.substring(ix + 1);
						mcache = getFolderCache(fromfolder);
						iids[0] = Integer.parseInt(id);
						moveMessages(mcache, tomcache, iids);
					}
				}
				long millis = System.currentTimeMillis();
				sout = "{\nresult: true, millis: " + millis + "\n}";
			} else {
				ids = getMessageIds(mcache, request);
				moveMessages(mcache, tomcache, toInts(ids));
				tomcache.refreshUnreads();
				mcache.setForceRefresh();
				long millis = System.currentTimeMillis();
				sout = "{\nresult: true, unread: " + tomcache.getUnreadMessagesCount() + ", millis: " + millis + "\n}";
			}
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processCopyMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String tofolder = request.getParameter("tofolder");
		String allfiltered = request.getParameter("allfiltered");
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String sout = null;
		String ids[] = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			FolderCache tomcache = getFolderCache(tofolder);
			if (allfiltered == null) {
				ids = request.getParameterValues("ids");
				if (!mf) {
					copyMessages(mcache, tomcache, toInts(ids));
				} else {
					int iids[] = new int[1];
					for (String id : ids) {
						int ix = id.indexOf("|");
						fromfolder = id.substring(0, ix);
						id = id.substring(ix + 1);
						mcache = getFolderCache(fromfolder);
						iids[0] = Integer.parseInt(id);
						copyMessages(mcache, tomcache, iids);
					}
				}
				long millis = System.currentTimeMillis();
				sout = "{\nresult: true, millis: " + millis + "\n}";
			} else {
				ids = getMessageIds(mcache, request);
				copyMessages(mcache, tomcache, toInts(ids));
				tomcache.refreshUnreads();
				mcache.setForceRefresh();
				long millis = System.currentTimeMillis();
				sout = "{\nresult: true, unread: " + tomcache.getUnreadMessagesCount() + ", millis: " + millis + "\n}";
			}
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processArchiveMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String tofolder = request.getParameter("tofolder");
		int ix = tofolder.indexOf("|");
		String idcategory = tofolder.substring(0, ix);
		String idsubcategory = tofolder.substring(ix + 1);
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String ids[] = null;
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			ids = request.getParameterValues("ids");
			if (!mf) {
				archiveMessages(mcache, toInts(ids), idcategory, idsubcategory);
			} else {
				int iids[] = new int[1];
				for (String id : ids) {
					ix = id.indexOf("|");
					fromfolder = id.substring(0, ix);
					id = id.substring(ix + 1);
					mcache = getFolderCache(fromfolder);
					iids[0] = Integer.parseInt(id);
					archiveMessages(mcache, iids, idcategory, idsubcategory);
				}
			}
			long millis = System.currentTimeMillis();
			sout = "{\nresult: true, millis: " + millis + "\n}";
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processArchiveMessagesWt(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String tofolder = request.getParameter("tofolder");
		String customer_id = request.getParameter("customer_id");
		int ix = tofolder.indexOf("/");
		String idcategory = tofolder;
		String idsubcategory = "";
		if (ix > 0) {
			idcategory = tofolder.substring(0, ix);
			idsubcategory = tofolder.substring(ix + 1);
		}
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String ids[] = null;
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			ids = request.getParameterValues("ids");
			if (!mf) {
				archiveMessagesWt(mcache, toInts(ids), idcategory, idsubcategory, customer_id);
			} else {
				int iids[] = new int[1];
				for (String id : ids) {
					ix = id.indexOf("/");
					fromfolder = id.substring(0, ix);
					id = id.substring(ix + 1);
					mcache = getFolderCache(fromfolder);
					iids[0] = Integer.parseInt(id);
					archiveMessagesWt(mcache, iids, idcategory, idsubcategory, customer_id);
				}
			}
			long millis = System.currentTimeMillis();
			sout = "{\nresult: true, millis: " + millis + "\n}";
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	String[] getMessageIds(FolderCache mcache, HttpServletRequest request) throws MessagingException {
		String psortfield = request.getParameter("sort");
		String psortdir = request.getParameter("dir");
		String psearchfield = request.getParameter("searchfield");
		String ppattern = request.getParameter("pattern");
		if (psearchfield != null && psearchfield.trim().length() == 0) {
			psearchfield = null;
		}
		if (ppattern != null && ppattern.trim().length() == 0) {
			ppattern = null;
		}
		if (psortfield == null) {
			psortfield = "date";
		}
		if (psortdir == null) {
			psortdir = "DESC";
		}
		int sortby = 0;
		if (psortfield.equals("messageid")) {
			sortby = MessageComparator.SORT_BY_MSGIDX;
		} else if (psortfield.equals("date")) {
			sortby = MessageComparator.SORT_BY_DATE;
		} else if (psortfield.equals("priority")) {
			sortby = MessageComparator.SORT_BY_PRIORITY;
		} else if (psortfield.equals("to")) {
			sortby = MessageComparator.SORT_BY_RCPT;
		} else if (psortfield.equals("from")) {
			sortby = MessageComparator.SORT_BY_SENDER;
		} else if (psortfield.equals("size")) {
			sortby = MessageComparator.SORT_BY_SIZE;
		} else if (psortfield.equals("subject")) {
			sortby = MessageComparator.SORT_BY_SUBJECT;
		} else if (psortfield.equals("status")) {
			sortby = MessageComparator.SORT_BY_STATUS;
		} else if (psortfield.equals("flag")) {
			sortby = MessageComparator.SORT_BY_FLAG;
		}
		boolean ascending = psortdir.equals("ASC");
		
		String group = mus.getMessageListGroup(mcache.getFolderName());
		if (group == null) {
			group = "";
		}
		
		int sort_group = 0;
		boolean groupascending = true;
		if (group.equals("messageid")) {
			sort_group = MessageComparator.SORT_BY_MSGIDX;
		} else if (group.equals("gdate")) {
			sort_group = MessageComparator.SORT_BY_DATE;
			groupascending = false;
		} else if (group.equals("priority")) {
			sort_group = MessageComparator.SORT_BY_PRIORITY;
		} else if (group.equals("to")) {
			sort_group = MessageComparator.SORT_BY_RCPT;
		} else if (group.equals("from")) {
			sort_group = MessageComparator.SORT_BY_SENDER;
		} else if (group.equals("size")) {
			sort_group = MessageComparator.SORT_BY_SIZE;
		} else if (group.equals("subject")) {
			sort_group = MessageComparator.SORT_BY_SUBJECT;
		} else if (group.equals("status")) {
			sort_group = MessageComparator.SORT_BY_STATUS;
		} else if (group.equals("flag")) {
			sort_group = MessageComparator.SORT_BY_FLAG;
		}
		
		Message msgs[] = mcache.getMessages(ppattern, psearchfield, sortby, ascending, false, sort_group, groupascending);
		ArrayList<String> aids = new ArrayList<String>();
		for (Message m : msgs) {
			aids.add("" + m.getMessageNumber());
			/*            String xids[];
			 try {
			 xids=m.getHeader("Message-ID");
			 } catch(MessagingException exc) {
			 continue;
			 }
			 if (xids!=null && xids.length>0) aids.add(xids[0]);*/
		}
		String ids[] = new String[aids.size()];
		aids.toArray(ids);
		return ids;
	}
	
	public void processDeleteMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String ids[] = request.getParameterValues("ids");
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			if (!mf) {
				deleteMessages(mcache, toInts(ids));
			} else {
				int iids[] = new int[1];
				for (String id : ids) {
					int ix = id.indexOf("|");
					fromfolder = id.substring(0, ix);
					id = id.substring(ix + 1);
					mcache = getFolderCache(fromfolder);
					iids[0] = Integer.parseInt(id);
					deleteMessages(mcache, iids);
				}
			}
			sout = "{\nresult: true\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processFlagMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String ids[] = request.getParameterValues("ids");
		String flag = request.getParameter("flag");
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			if (!mf) {
				if (flag.equals("clear")) {
					clearMessagesFlag(mcache, toInts(ids));
				} else {
					flagMessages(mcache, toInts(ids), flag);
				}
			} else {
				int iids[] = new int[1];
				for (String id : ids) {
					int ix = id.indexOf("|");
					fromfolder = id.substring(0, ix);
					id = id.substring(ix + 1);
					mcache = getFolderCache(fromfolder);
					iids[0] = Integer.parseInt(id);
					if (flag.equals("clear")) {
						clearMessagesFlag(mcache, iids);
					} else {
						flagMessages(mcache, iids, flag);
					}
				}
			}
			sout = "{\nresult: true\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processSeenMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String ids[] = request.getParameterValues("ids");
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			if (!mf) {
				setMessagesSeen(mcache, toInts(ids));
			} else {
				int iids[] = new int[1];
				for (String id : ids) {
					int ix = id.indexOf("|");
					fromfolder = id.substring(0, ix);
					id = id.substring(ix + 1);
					mcache = getFolderCache(fromfolder);
					iids[0] = Integer.parseInt(id);
					setMessagesSeen(mcache, iids);
				}
			}
			long millis = System.currentTimeMillis();
			sout = "{\nresult: true, millis: " + millis + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processUnseenMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String fromfolder = request.getParameter("fromfolder");
		String ids[] = request.getParameterValues("ids");
		String multifolder = request.getParameter("multifolder");
		boolean mf = multifolder != null && multifolder.equals("true");
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(fromfolder);
			if (!mf) {
				setMessagesUnseen(mcache, toInts(ids));
			} else {
				int iids[] = new int[1];
				for (String id : ids) {
					int ix = id.indexOf("|");
					fromfolder = id.substring(0, ix);
					id = id.substring(ix + 1);
					mcache = getFolderCache(fromfolder);
					iids[0] = Integer.parseInt(id);
					setMessagesUnseen(mcache, iids);
				}
			}
			long millis = System.currentTimeMillis();
			sout = "{\nresult: true, millis: " + millis + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processSetScanFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		boolean value = false;
		String svalue = request.getParameter("value");
		if (svalue != null) {
			value = svalue.equals("1");
		}
		boolean recursive = false;
		String srecursive = request.getParameter("recursive");
		if (srecursive != null) {
			recursive = srecursive.equals("1");
		}
		String sout = null;
		Connection con = null;
		Statement stmt = null;
		try {
			con = getConnection();
			stmt = con.createStatement();
			FolderCache fc = getFolderCache(folder);
			setScanFolder(stmt, fc, value, recursive);
			sout = "{\nresult: true\n}";
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (SQLException exc) {
				}
			}
		}
		out.println(sout);
	}
	
	private void setScanFolder(Statement stmt, FolderCache fc, boolean value, boolean recursive) throws SQLException {
		UserProfile profile = environment.getProfile();
		String login = profile.getUserId();
		String folder = fc.getFolderName();
		if (value) {
			stmt.executeUpdate("insert into mailscan values ('" + login + "','" + OldUtils.getSQLString(folder) + "','" + profile.getDomainId() + "')");
			fc.setScanEnabled(true);
		} else {
			stmt.executeUpdate("delete from mailscan where iddomain='" + profile.getDomainId() + "' and login='" + login + "' and foldername='" + OldUtils.getSQLString(folder) + "'");
			fc.setScanEnabled(false);
		}
		if (recursive) {
			ArrayList<FolderCache> children = fc.getChildren();
			if (children != null) {
				for (FolderCache child : children) {
					setScanFolder(stmt, child, value, recursive);
				}
			}
		}
	}
	
	public void processSeenFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		boolean recursive = false;
		String srecursive = request.getParameter("recursive");
		if (srecursive != null) {
			recursive = srecursive.equals("1");
		}
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			boolean result = true;
			sout = "{\n";
			mcache = getFolderCache(folder);
			setMessagesSeen(mcache, true, recursive);
			long millis = System.currentTimeMillis();
			sout += "result: " + result + ", millis: " + millis + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + folder + "', oldname: '" + (mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processUnseenFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		boolean recursive = false;
		String srecursive = request.getParameter("recursive");
		if (srecursive != null) {
			recursive = srecursive.equals("1");
		}
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			boolean result = true;
			sout = "{\n";
			mcache = getFolderCache(folder);
			setMessagesSeen(mcache, false, recursive);
			sout += "result: " + result + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + folder + "', oldname: '" + (mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	private void setMessagesSeen(FolderCache mcache, boolean seen, boolean recursive) throws MessagingException {
		if (seen) {
			mcache.setMessagesSeen();
		} else {
			mcache.setMessagesUnseen();
		}
		if (recursive) {
			ArrayList<FolderCache> children = mcache.getChildren();
			if (children != null) {
				for (FolderCache fc : children) {
					setMessagesSeen(fc, seen, recursive);
				}
			}
		}
	}
	
	public String getPartName(Part p) throws MessagingException {
		String pname = p.getFileName();
		if (pname == null) {
			String hctypes[] = p.getHeader("Content-Type");
			if (hctypes == null || hctypes.length == 0) {
				return null;
			}
			String hctype = hctypes[0];
			int ix = hctype.indexOf("name=");
			if (ix >= 0) {
				int sx = ix + 5;
				int ex = hctype.indexOf(";", sx);
				if (ex >= 0) {
					pname = hctype.substring(sx, ex);
				} else {
					pname = hctype.substring(sx);
				}
				pname = pname.trim();
				int xx = pname.length() - 1;
				if (pname.charAt(0) == '"' && pname.charAt(xx) == '"') {
					pname = pname.substring(1, xx);
				}
			}
			if (pname == null) {
				return null;
			}
		}
		try {
			pname = OldUtils.decodeQString(pname, "iso-8859-1");
		} catch (Exception exc) {
			exc.printStackTrace();
		}
		return pname;
	}

	// TODO: save column size!!!
/*    public void processSaveColumnSize(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String name=request.getParameter("name");
	 String size=request.getParameter("size");
	 wts.setServiceSetting("mail", "column-"+name, size);
	 out.println("{ result: true }");
	 }*/
	// TODO: save column visibility
/*	public void processSaveColumnVisibility(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		
	 try {
	 String folder = ServletUtils.getStringParameter(request, "folder", true);
	 String name = ServletUtils.getStringParameter(request, "name", true);
	 Boolean visible = ServletUtils.getBooleanParameter(request, "visible", false);
			
	 ColumnVisibilitySetting cvs = mus.getColumnVisibilitySetting(folder);
	 FolderCache fc = getFolderCache(folder);
			
	 cvs.put(name, visible);
	 // Handle default cases...avoid data waste!
	 if(ColumnVisibilitySetting.isDefault(fc.isSent(), name, cvs.get(name))) cvs.remove(name);
			
	 if(cvs.isEmpty()) {
	 wtd.deleteServiceSetting("mail", environment.getUserProfile(), key);
	 } else {
	 wtd.setServiceSetting("mail", environment.getUserProfile(), key, JsonResult.gsonWoNulls.toJson(cvs));
	 }
	 new JsonResult().printTo(out);
			
	 } catch(Exception ex) {
	 logger.error("Error saving column visibility.", ex);
	 new JsonResult(false).printTo(out);
	 }
	 }*/
	// TODO: save pane size
/*    public void processSavePaneSize(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String hperc=request.getParameter("hperc");
	 wts.setServiceSetting("mail", "pane-hperc", hperc);
	 out.println("{ result: true }");
	 }*/
	public void processNewFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		String name = request.getParameter("name");
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			Folder newfolder = null;
			boolean result = true;
			sout = "{\n";
			name = normalizeName(name);
			if (folder == null) {
				mcache = fcRoot;
			} else {
				mcache = getFolderCache(folder);
			}
			newfolder = mcache.createFolder(name);
			if (newfolder == null) {
				result = false;
			} else {
				if (mcache != fcRoot) {
					sout += "parent: '" + OldUtils.jsEscape(mcache.getFolderName()) + "',\n";
				} else {
					sout += "parent: null,\n";
				}
				sout += "name: '" + OldUtils.jsEscape(newfolder.getName()) + "',\n";
				sout += "fullname: '" + OldUtils.jsEscape(newfolder.getFullName()) + "',\n";
			}
			sout += "result: " + result + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + OldUtils.jsEscape(folder) + "', oldname: '" + OldUtils.jsEscape(mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processRenameFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		String name = request.getParameter("name");
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			boolean result = true;
			sout = "{\n";
			mcache = getFolderCache(folder);
			name = normalizeName(name);
			String newid = renameFolder(folder, name);
			sout += "oldid: '" + OldUtils.jsEscape(folder) + "',\n";
			sout += "newid: '" + OldUtils.jsEscape(newid) + "',\n";
			sout += "newname: '" + OldUtils.jsEscape(name) + "',\n";
			sout += "result: " + result + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + OldUtils.jsEscape(folder) + "', oldname: '" + OldUtils.jsEscape(mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	private String normalizeName(String name) throws MessagingException {
		String sep = "" + folderSeparator;
		while (name.contains(sep)) {
			name = name.replace(sep, "_");
		}
		return name;
	}
	
	public void processDeleteFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			boolean result = true;
			sout = "{\n";
			mcache = getFolderCache(folder);
			if (isSpecialFolder(folder)) {
				result = false;
			} else {
				result = deleteFolder(folder);
				if (result) {
					sout += "oldid: '" + OldUtils.jsEscape(folder) + "',\n";
				}
			}
			sout += "result: " + result + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + OldUtils.jsEscape(folder) + "', oldname: '" + OldUtils.jsEscape(mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processTrashFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			boolean result = true;
			sout = "{\n";
			mcache = getFolderCache(folder);
			if (isSpecialFolder(folder)) {
				result = false;
			} else {
				FolderCache newfc = trashFolder(folder);
				Folder newf = newfc.getFolder();
				sout += "oldid: '" + OldUtils.jsEscape(folder) + "',\n";
				sout += "newid: '" + OldUtils.jsEscape(newf.getFullName()) + "',\n";
				sout += "newname: '" + OldUtils.jsEscape(newf.getName()) + "',\n";
				result = true;
			}
			sout += "result: " + result + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + OldUtils.jsEscape(folder) + "', oldname: '" + OldUtils.jsEscape(mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processMoveFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		String to = request.getParameter("to");
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			boolean result = true;
			sout = "{\n";
			mcache = getFolderCache(folder);
			if (isSpecialFolder(folder)) {
				result = false;
			} else {
				FolderCache newfc = moveFolder(folder, to);
				Folder newf = newfc.getFolder();
				sout += "oldid: '" + OldUtils.jsEscape(folder) + "',\n";
				sout += "newid: '" + OldUtils.jsEscape(newf.getFullName()) + "',\n";
				sout += "newname: '" + OldUtils.jsEscape(newf.getName()) + "',\n";
				if (to != null) {
					sout += "parent: '" + OldUtils.jsEscape(newf.getParent().getFullName()) + "',\n";
				}
				result = true;
			}
			sout += "result: " + result + "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + OldUtils.jsEscape(folder) + "', oldname: '" + OldUtils.jsEscape(mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processEmptyFolder(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String folder = request.getParameter("folder");
		String sout = null;
		FolderCache mcache = null;
		try {
			checkStoreConnected();
			sout = "{\n";
			mcache = getFolderCache(folder);
			emptyFolder(folder);
			sout += "oldid: '" + OldUtils.jsEscape(folder) + "',\n";
			sout += "result: true\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, oldid: '" + OldUtils.jsEscape(folder) + "', oldname: '" + OldUtils.jsEscape(mcache != null ? mcache.getFolder().getName() : "unknown") + "', text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processGetSource(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String foldername = request.getParameter("folder");
		String id = request.getParameter("id");
		String sheaders = request.getParameter("headers");
		boolean headers = sheaders.equals("true");
		String sout = null;
		try {
			checkStoreConnected();
			StringBuffer sb = new StringBuffer("<pre>");
			FolderCache mcache = getFolderCache(foldername);
			Message msg = mcache.getMessage(Integer.parseInt(id));
			//Folder folder=msg.getFolder();
			for (Enumeration e = msg.getAllHeaders(); e.hasMoreElements();) {
				Header header = (Header) e.nextElement();
				sb.append(OldUtils.htmlescape(header.getName()) + ": " + OldUtils.htmlescape(header.getValue()) + "\n");
			}
			if (!headers) {
				BufferedReader br = new BufferedReader(new InputStreamReader(msg.getInputStream()));
				String line = null;
				while ((line = br.readLine()) != null) {
					sb.append(OldUtils.htmlescape(line) + "\n");
				}
			}
			sb.append("</pre>");
			sout = "{\nresult: true, source: '" + OldUtils.jsEscape(sb.toString()) + "'\n}";
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processSaveMail(HttpServletRequest request, HttpServletResponse response) {
		String foldername = request.getParameter("folder");
		String id = request.getParameter("id");
		String sout = null;
		try {
			checkStoreConnected();
			StringBuffer sb = new StringBuffer();
			FolderCache mcache = getFolderCache(foldername);
			Message msg = mcache.getMessage(Integer.parseInt(id));
			String subject = msg.getSubject();
			String ctype = "binary/octet-stream";
			response.setContentType(ctype);
			response.setHeader("Content-Disposition", "inline; filename=\"" + subject + ".eml\"");
			OutputStream out = response.getOutputStream();
			msg.writeTo(out);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processDownloadMails(HttpServletRequest request, HttpServletResponse response) {
		String foldername = request.getParameter("folder");
		String sout = null;
		try {
			checkStoreConnected();
			StringBuffer sb = new StringBuffer();
			FolderCache mcache = getFolderCache(foldername);
			Message msgs[] = mcache.getAllMessages();
			String zipname = "Webtop-mails-" + getInternationalFolderName(mcache);
			response.setContentType("application/x-zip-compressed");
			response.setHeader("Content-Disposition", "inline; filename=\"" + zipname + ".zip\"");
			OutputStream out = response.getOutputStream();
			
			JarOutputStream jos = new java.util.jar.JarOutputStream(out);
			int digits = (msgs.length > 0 ? (int) Math.log10(msgs.length) + 1 : 1);
			for (int i = 0; i < msgs.length; ++i) {
				Message msg = msgs[i];
				String subject = msg.getSubject();
				if (subject != null) {
					subject = subject.replace('/', '_').replace('\\', '_').replace(':', '-');
				} else {
					subject = "";
				}
				java.util.Date date = msg.getReceivedDate();
				if (date == null) {
					date = new java.util.Date();
				}
				
				String fname = OldUtils.zerofill(i + 1, digits) + " - " + subject + ".eml";
				JarEntry je = new JarEntry(fname);
				je.setTime(date.getTime());
				jos.putNextEntry(je);
				msg.writeTo(jos);
			}
			jos.closeEntry();
			jos.flush();
			jos.close();
			
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processGetReplyMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		UserProfile profile = environment.getProfile();
		//WebTopApp webtopapp=environment.getWebTopApp();
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String preplyall = request.getParameter("replyall");
		boolean replyAll = false;
		if (preplyall != null && preplyall.equals("1")) {
			replyAll = true;
		}
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(pfoldername);
			Message m = mcache.getMessage(Integer.parseInt(pidmessage));
			if (m.isExpunged()) {
				throw new MessagingException("Message " + pidmessage + " expunged");
			}
			SimpleMessage smsg = getReplyMsg(
					getNewMessageID(), m, replyAll, true,
					profile.getEmailAddress(), mprofile.isIncludeMessageInReply(),
					lookupResource(MailLocaleKey.MSG_FROMTITLE),
					lookupResource(MailLocaleKey.MSG_TOTITLE),
					lookupResource(MailLocaleKey.MSG_CCTITLE),
					lookupResource(MailLocaleKey.MSG_DATETITLE),
					lookupResource(MailLocaleKey.MSG_SUBJECTTITLE)
			);
			sout = "{\n result: true,";
			String inreplyto = smsg.getInReplyTo();
			String references[] = smsg.getReferences();
			if (inreplyto != null) {
				sout += " replyfolder: '" + OldUtils.jsEscape(pfoldername) + "',";
				sout += " inreplyto: '" + OldUtils.jsEscape(inreplyto) + "',";
			}
			if (references != null) {
				String refs = "";
				for (String s : references) {
					refs += OldUtils.jsEscape(s) + " ";
				}
				sout += " references: '" + refs.trim() + "',";
			}
			String subject = smsg.getSubject();
			sout += " subject: '" + OldUtils.jsEscape(subject) + "',\n";
			sout += " recipients: [\n";
			String tos[] = smsg.getTo().split(";");
			boolean first = true;
			for (String to : tos) {
				if (!first) {
					sout += ",\n";
				}
				sout += "   {rtype:'to',email:'" + OldUtils.jsEscape(to) + "'}";
				first = false;
			}
			String ccs[] = smsg.getCc().split(";");
			for (String cc : ccs) {
				if (!first) {
					sout += ",\n";
				}
				sout += "   {rtype:'cc',email:'" + OldUtils.jsEscape(cc) + "'}";
				first = false;
			}
			sout += "\n ],\n";
			String html = smsg.getContent();
			sout += " html:'" + OldUtils.jsEscape(html) + "'\n";
			sout += "\n}";
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		if (sout != null) {
			out.println(sout);
		}
	}
	
	public void processGetForwardMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		UserProfile profile = environment.getProfile();
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String pnewmsgid = request.getParameter("newmsgid");
		String pattached = request.getParameter("attached");
		boolean attached = (pattached != null && pattached.equals("1"));
		int newmsgid = Integer.parseInt(pnewmsgid);
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(pfoldername);
			Message m = mcache.getMessage(Integer.parseInt(pidmessage));
			if (m.isExpunged()) {
				throw new MessagingException("Message " + pidmessage + " expunged");
			}
			SimpleMessage smsg = getForwardMsg(
					newmsgid, m, true,
					lookupResource(MailLocaleKey.MSG_FROMTITLE),
					lookupResource(MailLocaleKey.MSG_TOTITLE),
					lookupResource(MailLocaleKey.MSG_CCTITLE),
					lookupResource(MailLocaleKey.MSG_DATETITLE),
					lookupResource(MailLocaleKey.MSG_SUBJECTTITLE),
					attached
			);
			
			sout = "{\n result: true,";
			String forwardedfrom = smsg.getForwardedFrom();
			if (forwardedfrom != null) {
				sout += " forwardedfolder: '" + OldUtils.jsEscape(pfoldername) + "',";
				sout += " forwardedfrom: '" + OldUtils.jsEscape(forwardedfrom) + "',";
			}
			String subject = smsg.getSubject();
			sout += " subject: '" + OldUtils.jsEscape(subject) + "',\n";
			
			String html = smsg.getContent();
			if (!attached) {
				HTMLMailData maildata = mcache.getMailData((MimeMessage) m);
				boolean first = true;
				sout += " attachments: [\n";
				for (int i = 0; i < maildata.getAttachmentPartCount(); ++i) {
					Part part = maildata.getAttachmentPart(i);
					String filename = getPartName(part);
					if (filename != null) {
						String cids[] = part.getHeader("Content-ID");
						String cid = null;
						//String cid=filename;
						if (cids != null && cids[0] != null) {
							cid = cids[0];
						}
						File tempFile = File.createTempFile("strts", null, new File(environment.getCoreServiceSettings().getTempPath()));
						createFile(part.getInputStream(), tempFile);
						boolean inline = false;
						if (part.getDisposition() != null) {
							inline = part.getDisposition().equalsIgnoreCase(Part.INLINE);
						}
						attachFile(newmsgid, tempFile, filename, part.getContentType(), cid, inline);
						String tempname = tempFile.getName();
						if (!first) {
							sout += ",\n";
						}
						sout += "{ name: '" + OldUtils.jsEscape(filename) + "', tempname: '" + OldUtils.jsEscape(tempname) + "' }";
						first = false;
					}
				}
				sout += "\n ],\n";
				String surl = "service-request?service="+getId()+"&action=PreviewAttachment&nowriter=true&newmsgid=" + newmsgid + "&cid=";
				html = replaceCidUrls(html, maildata, surl);
			} else {
				String filename = m.getSubject() + ".eml";
				File tempFile = File.createTempFile("strts", null, new File(environment.getCoreServiceSettings().getTempPath()));
				m.writeTo(new FileOutputStream(tempFile));
				//wta.createFile(m.getInputStream(), tempFile);
				attachFile(newmsgid, tempFile, filename, "message/rfc822", null, false);
				String tempname = tempFile.getName();
				sout += " attachments: [\n";
				sout += "{ name: '" + OldUtils.jsEscape(filename) + "', tempname: '" + OldUtils.jsEscape(tempname) + "' }";
				sout += "\n ],\n";
			}
			
			sout += " html:'" + OldUtils.jsEscape(html) + "'\n";
			sout += "\n}";
			out.println(sout);
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
	}
	
	public void processGetEditMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String pnewmsgid = request.getParameter("newmsgid");
		int newmsgid = Integer.parseInt(pnewmsgid);
		String sout = null;
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(pfoldername);
			Message m = mcache.getMessage(Integer.parseInt(pidmessage));
			String vheader[] = m.getHeader("Disposition-Notification-To");
			boolean receipt = false;
			int priority = 3;
			boolean recipients = false;
			if (isDraftsFolder(pfoldername)) {
				if (vheader != null && vheader[0] != null) {
					receipt = true;
				}
				priority = getPriority(m);
				recipients = true;
			}
			
			sout = "{\n result: true,";
			String subject = m.getSubject();
			if (subject == null) {
				subject = "";
			}
			sout += " subject: '" + OldUtils.jsEscape(subject) + "',\n";
			
			String inreplyto = null;
			String references[] = null;
			String vs[] = m.getHeader("In-Reply-To");
			if (vs != null && vs[0] != null) {
				inreplyto = vs[0];
			}
			references = m.getHeader("References");
			if (inreplyto != null) {
				vs = m.getHeader("Sonicle-reply-folder");
				String replyfolder = null;
				if (vs != null && vs[0] != null) {
					replyfolder = vs[0];
				}
				if (replyfolder != null) {
					sout += " replyfolder: '" + OldUtils.jsEscape(replyfolder) + "',";
				}
				sout += " inreplyto: '" + OldUtils.jsEscape(inreplyto) + "',";
			}
			if (references != null) {
				String refs = "";
				for (String s : references) {
					refs += OldUtils.jsEscape(s) + " ";
				}
				sout += " references: '" + refs.trim() + "',";
			}
			
			String forwardedfrom = null;
			vs = m.getHeader("Forwarded-From");
			if (vs != null && vs[0] != null) {
				forwardedfrom = vs[0];
			}
			if (forwardedfrom != null) {
				vs = m.getHeader("Sonicle-forwarded-folder");
				String forwardedfolder = null;
				if (vs != null && vs[0] != null) {
					forwardedfolder = vs[0];
				}
				if (forwardedfolder != null) {
					sout += " forwardedfolder: '" + OldUtils.jsEscape(forwardedfolder) + "',";
				}
				sout += " forwardedfrom: '" + OldUtils.jsEscape(forwardedfrom) + "',";
			}
			
			sout += " receipt: " + receipt + ",\n";
			sout += " priority: " + (priority >= 3 ? false : true) + ",\n";
			sout += " recipients: [\n";
			if (recipients) {
				Address tos[] = m.getRecipients(RecipientType.TO);
				String srec = "";
				if (tos != null) {
					for (Address to : tos) {
						if (srec.length() > 0) {
							srec += ",\n";
						}
						srec += "   { "
								+ "type: 'to', "
								+ "address: '" + OldUtils.jsEscape(getDecodedAddress(to)) + "'"
								+ " }";
					}
				}
				Address ccs[] = m.getRecipients(RecipientType.CC);
				if (ccs != null) {
					for (Address cc : ccs) {
						if (srec.length() > 0) {
							srec += ",\n";
						}
						srec += "   { "
								+ "type: 'cc', "
								+ "address: '" + OldUtils.jsEscape(getDecodedAddress(cc)) + "'"
								+ " }";
					}
				}
				Address bccs[] = m.getRecipients(RecipientType.BCC);
				if (bccs != null) {
					for (Address bcc : bccs) {
						if (srec.length() > 0) {
							srec += ",\n";
						}
						srec += "   { "
								+ "type: 'bcc', "
								+ "address: '" + OldUtils.jsEscape(getDecodedAddress(bcc)) + "'"
								+ " }";
					}
				}
				
				sout += srec;
			}
			sout += " ],\n";
			
			String html = "";
			ArrayList<String> htmlparts = mcache.getHTMLParts((MimeMessage) m, newmsgid, true);
			for (String xhtml : htmlparts) {
				html += xhtml + "<BR><BR>";
			}
			HTMLMailData maildata = mcache.getMailData((MimeMessage) m);
			boolean first = true;
			sout += " attachments: [\n";
			for (int i = 0; i < maildata.getAttachmentPartCount(); ++i) {
				Part part = maildata.getAttachmentPart(i);
				String filename = part.getFileName();
				if (filename != null) {
					String cids[] = part.getHeader("Content-ID");
					String cid = null;
					//String cid=filename;
					if (cids != null && cids[0] != null) {
						cid = cids[0];
						if (cid.startsWith("<")) {
							cid = cid.substring(1);
						}
						if (cid.endsWith(">")) {
							cid = cid.substring(0, cid.length() - 1);
						}
					}
					File tempFile = File.createTempFile("strts", null, new File(environment.getCoreServiceSettings().getTempPath()));
					createFile(part.getInputStream(), tempFile);
					boolean inline = false;
					if (part.getDisposition() != null) {
						inline = part.getDisposition().equalsIgnoreCase(Part.INLINE);
					}
					attachFile(newmsgid, tempFile, filename, part.getContentType(), cid, inline);
					String tempname = tempFile.getName();
					if (!first) {
						sout += ",\n";
					}
					sout += "{ name: '" + OldUtils.jsEscape(filename) + "', tempname: '" + OldUtils.jsEscape(tempname) + "' }";
					first = false;
				}
			}
			sout += "\n ],\n";

            //System.out.println("HTML newmsgid="+newmsgid);
			//System.out.println(html);
			//String surl="service-requests?service=com.sonicle.webtop.mail&action=PreviewAttachment&nowriter=true&newmsgid="+newmsgid+"&cid=";
			//html=replaceCidUrls(html, maildata, surl);
			sout += " html:'" + OldUtils.jsEscape(html) + "'\n";
			sout += "\n}";
			out.println(sout);
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
	}

	// TODO: getEditProviderMessage!!!
/*    public void processGetEditProviderMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String providername=request.getParameter("provider");
	 String providerid=request.getParameter("providerid");
	 String pnewmsgid=request.getParameter("newmsgid");
	 WebTopService provider=wts.getServiceByName(providername);
	 MessageContentProvider mcp=provider.getMessageContentProvider(providerid);
	 int newmsgid=Integer.parseInt(pnewmsgid);
	 String sout=null;
	 try {
	 boolean receipt=mcp.getReceipt();
	 int priority=mcp.getPriority();
	 boolean recipients=false;

	 sout="{\n result: true,";
	 String subject=mcp.getSubject();
	 if (subject==null) subject="";
	 sout+=" subject: '"+OldUtils.jsEscape(subject)+"',\n";
	 sout+=" receipt: "+receipt+",\n";
	 sout+=" priority: "+(priority>=3?false:true)+",\n";
	 sout+=" recipients: [\n";
	 if (recipients) {
	 Address tos[]=mcp.getToRecipients();
	 String srec="";
	 if (tos!=null) {
	 for(Address to: tos) {
	 if (srec.length()>0) srec+=",\n";
	 srec+="   { "+
	 "type: 'to', "+
	 "address: '"+OldUtils.jsEscape(getDecodedAddress(to))+"'"+
	 " }";
	 }
	 }
	 Address ccs[]=mcp.getCcRecipients();
	 if (ccs!=null) {
	 for(Address cc: ccs) {
	 if (srec.length()>0) srec+=",\n";
	 srec+="   { "+
	 "type: 'cc', "+
	 "address: '"+OldUtils.jsEscape(getDecodedAddress(cc))+"'"+
	 " }";
	 }
	 }
	 Address bccs[]=mcp.getBccRecipients();
	 if (bccs!=null) {
	 for(Address bcc: bccs) {
	 if (srec.length()>0) srec+=",\n";
	 srec+="   { "+
	 "type: 'bcc', "+
	 "address: '"+OldUtils.jsEscape(getDecodedAddress(bcc))+"'"+
	 " }";
	 }
	 }

	 sout+=srec;
	 }
	 sout+=" ],\n";

	 String html=mcp.getHtml();
	 boolean first=true;
	 sout+=" attachments: [\n";
	 if (mcp.hasAttachments()) {
	 for (int i = 0; i < mcp.getAttachmentsCount(); ++i) {
	 String filename = mcp.getAttachmentName(i);
	 if (filename != null) {
	 File tempFile = File.createTempFile("strts", null, new File(wtd.getTempPath()));
	 wta.createFile(mcp.getAttachment(i), tempFile);
	 attachFile(newmsgid, tempFile, filename, mcp.getContentType(i), null, false);
	 String tempname=tempFile.getName();
	 if (!first) sout+=",\n";
	 sout+="{ name: '"+OldUtils.jsEscape(filename)+"', tempname: '"+OldUtils.jsEscape(tempname)+"' }";
	 first=false;
	 }
	 }
	 }
	 sout+="\n ],\n";

	 //System.out.println("HTML newmsgid="+newmsgid);
	 //System.out.println(html);
	 //String surl="service-requests?service=com.sonicle.webtop.mail&action=PreviewAttachment&nowriter=true&newmsgid="+newmsgid+"&cid=";
	 //html=replaceCidUrls(html, maildata, surl);

	 sout+=" html:'"+OldUtils.jsEscape(html)+"'\n";
	 sout+="\n}";
	 out.println(sout);
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 }
	 }*/
	public void processSendMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String sout = null;

		// TODO: Cloud integration!!!
/*        VFSService vfs=(VFSService)wts.getServiceByName("vfs");
		 ArrayList<String> hashlinks=null;
		 if (vfs!=null) {
		 //look for links to cloud in the html
		 String html=request.getParameter("content");
		 hashlinks=new ArrayList<String>();
		 int hlx=-1;
		 String puburl=wtd.getSetting("vfs.pub.url");
		 char chars[]=new char[] { '\'', '"'};
		 for(char c: chars) {
		 String pattern="<a href="+c+puburl+"/public/vfs/";
		 System.out.println("Looking for pattern "+pattern);
		 while((hlx=html.indexOf(pattern,hlx+1))>=0) {
		 int xhash1=hlx+pattern.length();
		 int xhash2=html.indexOf(c,xhash1);
		 if (xhash2>xhash1) {
		 String hash=html.substring(xhash1,xhash2);
		 System.out.println("Found hash "+hash);
		 hashlinks.add(hash);
		 }
		 }
		 }
		 }*/
        // TODO: Save used recipients
/*        String emails[]=request.getParameterValues("recipients");
		 for(String email: emails) {
		 if (email!=null && email.trim().length()>0) wts.setServiceStoreEntry(getName(), "recipients", email.toUpperCase(),email);
		 }*/
        // TODO: Save used subject
/*        String subject=request.getParameter("subject");
		 if (subject!=null && subject.trim().length()>0) wts.setServiceStoreEntry(getName(), "subject", subject.toUpperCase(),subject);*/
		try {
			checkStoreConnected();
			String attachments[] = request.getParameterValues("attachments");
			if (attachments == null) {
				attachments = new String[0];
			}
			SimpleMessage msg = prepareMessage(request);
			Identity ifrom = msg.getFrom();
			String from = environment.getProfile().getEmailAddress();
			if (ifrom != null) {
				from = ifrom.email;
			}
			checkStoreConnected();
			Exception exc = sendMessage(msg, attachments);
			if (exc == null) {
				String newmsgid = request.getParameter("newmsgid");

				//TODO: deleteAutoSaveData!!!!
				//wts.deleteAutoSaveData("mail","newmail",newmsgid);
				// TODO: Cloud integration!!!
/*                if (vfs!=null && hashlinks!=null && hashlinks.size()>0) {
				 for(String hash: hashlinks) {
				 System.out.println("Adding emails to hash "+hash);
				 vfs.setAuthEmails(hash, from, emails);
				 }

				 }*/
				FolderCache fc = getFolderCache(mprofile.getFolderSent());
				fc.setForceRefresh();
				//check for in-reply-to and set the answered flags
				String inreplyto = request.getParameter("inreplyto");
				String replyfolder = request.getParameter("replyfolder");
				String forwardedfrom = request.getParameter("forwardedfrom");
				String forwardedfolder = request.getParameter("forwardedfolder");
				String foundfolder = null;
				if (forwardedfrom != null && forwardedfrom.trim().length() > 0) {
					try {
						foundfolder = flagForwardedMessage(forwardedfolder, forwardedfrom);
					} catch (Exception xexc) {
						xexc.printStackTrace();
					}
				} else if (inreplyto != null && inreplyto.trim().length() > 0) {
					try {
						foundfolder = flagAnsweredMessage(replyfolder, inreplyto);
					} catch (Exception xexc) {
						xexc.printStackTrace();
					}
				}
				
				sout = "{\nresult: true,";
				if (foundfolder != null) {
					sout += " foundfolder: '" + OldUtils.jsEscape(foundfolder) + "',";
				}
				sout += " saved: false\n}";
			} else {
				Throwable cause = exc.getCause();
				String msgstr = cause != null ? cause.getMessage() : exc.getMessage();
				sout = "{\nresult: false, text:'" + OldUtils.jsEscape(msgstr) + "'\n}";
			}
		} catch (Exception exc) {
			exc.printStackTrace();
			Throwable cause = exc.getCause();
			String msg = cause != null ? cause.getMessage() : exc.getMessage();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(msg) + "'\n}";
		}
		out.println(sout);
	}
	
	private String flagAnsweredMessage(String replyfolder, String id) throws MessagingException {
		String foundfolder = null;
		if (replyfolder != null) {
			if (_flagAnsweredMessage(replyfolder, id)) {
				foundfolder = replyfolder;
			}
		}
		if (foundfolder == null) {
			SonicleIMAPFolder f = (SonicleIMAPFolder) store.getFolder("");
			SonicleIMAPFolder.RecursiveSearchResult rsr = f.recursiveSearchByMessageID(id, skipReplyFolders);
			if (rsr != null) {
				_flagAnsweredMessage(rsr.foldername, rsr.uid);
				foundfolder = rsr.foldername;
			}
		}
		return foundfolder;
	}
	
	private boolean _flagAnsweredMessage(String foldername, long uid) throws MessagingException {
		Message msg = null;
		SonicleIMAPFolder sifolder = (SonicleIMAPFolder) store.getFolder(foldername);
		sifolder.open(Folder.READ_WRITE);
		msg = sifolder.getMessageByUID(uid);
		boolean found = msg != null;
		if (found) {
			msg.setFlags(FolderCache.repliedFlags, true);
		}
		sifolder.close(true);
		return found;
	}
	
	private boolean _flagAnsweredMessage(String foldername, String id) throws MessagingException {
		Message msgs[] = null;
		SonicleIMAPFolder sifolder = (SonicleIMAPFolder) store.getFolder(foldername);
		sifolder.open(Folder.READ_WRITE);
		msgs = sifolder.search(new MessageIDTerm(id));
		boolean found = (msgs != null && msgs.length > 0);
		if (found) {
			msgs[0].setFlags(FolderCache.repliedFlags, true);
		}
		sifolder.close(true);
		return found;
	}
	
	private String flagForwardedMessage(String forwardedfolder, String id) throws MessagingException {
		String foundfolder = null;
		if (forwardedfolder != null) {
			if (_flagForwardedMessage(forwardedfolder, id)) {
				foundfolder = forwardedfolder;
			}
		}
		if (foundfolder == null) {
			SonicleIMAPFolder f = (SonicleIMAPFolder) store.getFolder("");
			SonicleIMAPFolder.RecursiveSearchResult rsr = f.recursiveSearchByMessageID(id, skipForwardFolders);
			if (rsr != null) {
				_flagForwardedMessage(rsr.foldername, rsr.uid);
				foundfolder = rsr.foldername;
			}
		}
		return foundfolder;
	}
	
	private boolean _flagForwardedMessage(String foldername, long uid) throws MessagingException {
		Message msg = null;
		SonicleIMAPFolder sifolder = (SonicleIMAPFolder) store.getFolder(foldername);
		sifolder.open(Folder.READ_WRITE);
		msg = sifolder.getMessageByUID(uid);
		boolean found = msg != null;
		if (found) {
			msg.setFlags(FolderCache.forwardedFlags, true);
		}
		sifolder.close(true);
		return found;
	}
	
	private boolean _flagForwardedMessage(String foldername, String id) throws MessagingException {
		Message msgs[] = null;
		SonicleIMAPFolder sifolder = (SonicleIMAPFolder) store.getFolder(foldername);
		sifolder.open(Folder.READ_WRITE);
		msgs = sifolder.search(new MessageIDTerm(id));
		boolean found = (msgs != null && msgs.length > 0);
		if (found) {
			msgs[0].setFlags(FolderCache.forwardedFlags, true);
		}
		sifolder.close(true);
		return found;
	}
	
	public void processSaveMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String sout = null;
		try {
			checkStoreConnected();
			String attachments[] = request.getParameterValues("attachments");
			String savefolder = request.getParameter("savefolder");
			if (attachments == null) {
				attachments = new String[0];
			}
			SimpleMessage msg = prepareMessage(request);
			checkStoreConnected();
			FolderCache fc = null;
			if (savefolder == null) {
				fc = getFolderCache(mprofile.getFolderDrafts());
			} else {
				fc = getFolderCache(savefolder);
			}
			Exception exc = saveMessage(msg, attachments, fc);
			if (exc == null) {
				String newmsgid = request.getParameter("newmsgid");
				// TODO: deleteAutoSaveData!!!!
				//wts.deleteAutoSaveData("mail","newmail",newmsgid);
				
				fc.setForceRefresh();
				sout = "{\nresult: true, saved: true\n}";
			} else {
				sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
			}
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processScheduleMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String sout = null;
		try {
			checkStoreConnected();
			String attachments[] = request.getParameterValues("attachments");
			String savefolder = request.getParameter("savefolder");
			String scheddate = request.getParameter("scheddate");
			String schedtime = request.getParameter("schedtime");
			String schednotify = request.getParameter("schednotify");
			
			if (attachments == null) {
				attachments = new String[0];
			}
			SimpleMessage msg = prepareMessage(request);
			checkStoreConnected();
			FolderCache fc = null;
			if (savefolder == null) {
				fc = getFolderCache(mprofile.getFolderDrafts());
			} else {
				fc = getFolderCache(savefolder);
			}
			Exception exc = scheduleMessage(msg, attachments, fc, scheddate, schedtime, schednotify);
			if (exc == null) {
				String newmsgid = request.getParameter("newmsgid");
				// TODO: deleteAutoSaveData!!!!
				//wts.deleteAutoSaveData("mail","newmail",newmsgid);
				
				fc.setForceRefresh();
				sout = "{\nresult: true, saved: true\n}";
			} else {
				sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
			}
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}
	
	public void processDiscardMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String newmsgid = request.getParameter("newmsgid");
		clearAttachments(Integer.parseInt(newmsgid));
		// TODO: deleteAutoSaveData!!!!
		//wts.deleteAutoSaveData("mail","newmail",newmsgid);
		out.println("{\nresult: true\n}");
	}

	// TODO: sendNewsletter
/*    public void processSendNewsletter(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 sendNewsletter(request,response,out,false);
	 }

	 public void processSendNewsletterTest(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 sendNewsletter(request,response,out,true);
	 }

	 private void sendNewsletter(HttpServletRequest request, HttpServletResponse response, PrintWriter out, boolean test) {
	 String sout=null;
	 try {
	 //WebTopDomain wtd=environment.getWebTopDomain();
	 checkStoreConnected();
	 String attachments[]=request.getParameterValues("attachments");
	 if (attachments==null) attachments=new String[0];
	 SimpleMessage msg=prepareMessage(request);
	 checkStoreConnected();
	 String ssetting="newsletter.folder";
	 if (test) ssetting="newsletter.testfolder";
	 FolderCache fc=getFolderCache(wtd.getSetting(ssetting));
	 Exception exc=saveMessage(msg,attachments,fc);
	 if (exc==null) {
	 fc.setForceRefresh();
	 sout="{\nresult: true, saved: true\n}";
	 }
	 else sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 }
	 out.println(sout);
	 }*/
	private SimpleMessage prepareMessage(HttpServletRequest request) throws Exception {
		BasicEnvironment env = environment;
		//WebTopApp webtopapp=env.getWebTopApp();
		UserProfile profile = env.getProfile();
        //HttpSession session=request.getSession();
		//WebTopSession wts=(WebTopSession)session.getAttribute("webtopsession");

        //CharsetDecoder decoder=Charset.forName("UTF-16").newDecoder();
		String emails[] = request.getParameterValues("recipients");
		String rtypes[] = request.getParameterValues("rtypes");
		//expand multiple addresses
		ArrayList<String> aemails = new ArrayList<String>();
		ArrayList<String> artypes = new ArrayList<String>();
		for (int i = 0; i < emails.length; ++i) {
			StringTokenizer st = new StringTokenizer(emails[i], ";");
			while (st.hasMoreElements()) {
				String email = st.nextToken();
				aemails.add(email);
				artypes.add(rtypes[i]);
			}
		}
		emails = (String[]) aemails.toArray(emails);
		rtypes = (String[]) artypes.toArray(rtypes);
		
		String replyfolder = request.getParameter("replyfolder");
		String inreplyto = request.getParameter("inreplyto");
		String references = request.getParameter("references");
		
		String forwardedfolder = request.getParameter("forwardedfolder");
		String forwardedfrom = request.getParameter("forwardedfrom");
		
		String subject = request.getParameter("subject");
		String mime = request.getParameter("mime");
		String sident = request.getParameter("identity");
		String content = request.getParameter("content");
		String msgid = request.getParameter("newmsgid");
		String ssave = request.getParameter("save");
		boolean save = (ssave != null && ssave.equals("true"));
		String sreceipt = request.getParameter("receipt");
		boolean receipt = (sreceipt != null && sreceipt.equals("true"));
		String spriority = request.getParameter("priority");
		boolean priority = (spriority != null && spriority.equals("true"));
		
		boolean isFax = request.getParameter("fax") != null;
		
		String to = null;
		String cc = null;
		String bcc = null;
		for (int i = 0; i < emails.length; ++i) {
			//String email=decoder.decode(ByteBuffer.wrap(emails[i].getBytes())).toString();
			String email = emails[i];
			if (email == null || email.trim().length() == 0) {
				continue;
			}
			//Check for list
			boolean checkemail = true;
			boolean listdone = false;
			if (email.indexOf('@') < 0) {
				if (isFax && OldUtils.isNumeric(email)) {
					String faxpattern = mss.getFaxPattern();
					String faxemail = faxpattern.replace("{number}", email).replace("{username}", profile.getUserId());
					email = faxemail;
				} else {
					email = email.trim();
					String name = null;
					int ix0 = email.indexOf('<');
					if (ix0 < 0) {
						name = email;
					} else {
						name = email.substring(0, ix0).trim();
					}
					String dmid = null;
					int ix1 = email.indexOf('[');
					int ix2 = email.indexOf(']');
					if (ix1 >= 0 && ix2 > ix1) {
						name = email.substring(ix0 + 1, ix1 - 1);
						dmid = email.substring(ix1 + 1, ix2);
					} else {
						dmid = "privatedb: " + profile.getUserId();
					}

				// TODO: list integration!!!!
/*                ContactsService cs=(ContactsService)wts.getServiceByName("contacts");
					 DirectoryResult dr=cs.lookupDirectoryManager(dmid,name,false,false);
					 if (dr.getElementsCount()>0) {
					 DirectoryElement de=dr.elementAt(0);
					 String idlist=de.getField("IDLIST");
					 Connection con=null;
					 Statement stmt=null;
					 ResultSet rs=null;
					 try {
					 con=wtd.getConnection();
					 stmt=con.createStatement();
					 rs=stmt.executeQuery("select email,etype from listelements where idlist="+idlist);
					 email=null;
					 while (rs.next()) {
					 String xemail=rs.getString("email");
					 String xrtype=rs.getString("etype");
					 if (xemail!=null) {
					 try {
					 checkEmail(xemail);
					 InternetAddress.parse(xemail.replace(',', ' '),true);
					 } catch(AddressException exc) {
					 throw new AddressException(lookupResource(profile, MailLocaleKey.ADDRESS_ERROR)+" : "+xemail);
					 }
					 if (rtypes[i].equals("to")) {
					 if (xrtype.equals("to")) {
					 if (to==null) to=xemail;
					 else to+="; "+xemail;
					 } else if (xrtype.equals("cc")) {
					 if (cc==null) cc=xemail;
					 else cc+="; "+xemail;
					 } else if (xrtype.equals("bcc")) {
					 if (bcc==null) bcc=xemail;
					 else bcc+="; "+xemail;
					 }
					 } else if (rtypes[i].equals("cc")) {
					 if (cc==null) cc=xemail;
					 else cc+="; "+xemail;
					 } else if (rtypes[i].equals("bcc")) {
					 if (bcc==null) bcc=xemail;
					 else bcc+="; "+xemail;
					 }
					 }
					 }
					 listdone=true;
					 checkemail=false;
					 } catch(SQLException exc) {
					 //exc.printStackTrace();
					 } finally {
					 if (rs!=null) try { rs.close(); } catch(Exception exc2) {}
					 if (stmt!=null) try { stmt.close(); } catch(Exception exc2) {}
					 if (con!=null) try { con.close(); } catch(Exception exc2) {}
					 }
					 }*/
				}
			}
			if (listdone) {
				continue;
			}
			
			if (checkemail) {
				try {
					checkEmail(email);
					InternetAddress.parse(email.replace(',', ' '), false);
				} catch (AddressException exc) {
					exc.printStackTrace();
					throw new AddressException(lookupResource(MailLocaleKey.ADDRESS_ERROR) + " : " + email);
				}
			}
			
			if (rtypes[i].equals("to")) {
				if (to == null) {
					to = email;
				} else {
					to += "; " + email;
				}
			} else if (rtypes[i].equals("cc")) {
				if (cc == null) {
					cc = email;
				} else {
					cc += "; " + email;
				}
			} else if (rtypes[i].equals("bcc")) {
				if (bcc == null) {
					bcc = email;
				} else {
					bcc += "; " + email;
				}
			}
		}
		
		int id = Integer.parseInt(msgid);
		SimpleMessage msg = new SimpleMessage(id);
		int idx = Integer.parseInt(sident) - 1;
		Identity from = null;
		if (idx >= 0) {
			from = mprofile.getIdentity(idx);
		}
		msg.setFrom(from);
		msg.setTo(to);
		msg.setCc(cc);
		msg.setBcc(bcc);
		msg.setSubject(subject);
		
		if (isFax) {
			String coverpage = request.getParameter("faxcover");
			if (coverpage != null) {
				if (coverpage.equals("none")) {
					msg.addHeaderLine("X-FAX-AutoCoverPage: No");
				} else {
					msg.addHeaderLine("X-FAX-AutoCoverPage: Yes");
					msg.addHeaderLine("X-FAX-Cover-Template: " + coverpage);
				}
			}
		}
		
		String[] headersKeys = request.getParameterValues("headersKeys");
		String[] headersValues = request.getParameterValues("headersValues");
		if (headersKeys != null && headersValues != null && headersKeys.length == headersValues.length) {
			for (int i = 0; i < headersKeys.length; i++) {
				if (!headersKeys[i].equals("")) {
					msg.addHeaderLine(headersKeys[i] + ": " + headersValues[i]);
				}
			}
		}
		
		if (inreplyto != null) {
			msg.setInReplyTo(inreplyto);
		}
		if (references != null) {
			msg.setReferences(new String[]{references});
		}
		if (replyfolder != null) {
			msg.setReplyFolder(replyfolder);
		}
		
		if (forwardedfolder != null) {
			msg.setForwardedFolder(forwardedfolder);
		}
		if (forwardedfrom != null) {
			msg.setForwardedFrom(forwardedfrom);
		}
		
		msg.setReceipt(receipt);
		msg.setPriority(priority ? 1 : 3);
		if (mime == null || mime.equals("text/plain")) {
			msg.setContent(content);
		} else {
			if (mime.equalsIgnoreCase("text/html")) {
				/*                String surl=webtopapp.getUri();
				 int ix=surl.indexOf("?");
				 if (ix>=0) surl=surl.substring(0,ix);
				 ix=surl.lastIndexOf("/");
				 if (ix>=0) surl=surl.substring(0,ix);
				 surl=surl+"/Download?cid=";
				 content=OldUtils.replace(content,surl,"cid:");*/
            //String surl="PreviewAttachment\\?newmsgid\\="+msgid+"\\&cid\\=";
				//String surl="PreviewAttachment?newmsgid="+msgid+"&amp;cid=";

				//CIDs
				String surl = "service-request?service="+getId()+"&amp;action=PreviewAttachment&amp;nowriter=true&amp;newmsgid=" + msgid + "&amp;cid=";
				content = OldUtils.replace(content, surl, "cid:");
				surl = "service-request?service="+getId()+"&action=PreviewAttachment&nowriter=true&newmsgid=" + msgid + "&cid=";
				content = OldUtils.replace(content, surl, "cid:");
				//URLs
				surl = "service-request?service="+getId()+"&amp;action=PreviewAttachment&amp;nowriter=true&amp;newmsgid=" + msgid + "&amp;url=";
				content = OldUtils.replace(content, surl, "");
				surl = "service-request?service="+getId()+"&action=PreviewAttachment&nowriter=true&newmsgid=" + msgid + "&url=";
				content = OldUtils.replace(content, surl, "");
				String textcontent = OldUtils.HtmlToText_convert(OldUtils.htmlunescapesource(content));
				content = OldUtils.htmlescapefixsource(content);
				msg.setContent(content, textcontent, mime);
			} else {
				msg.setContent(content, null, mime);
			}
			
		}
		return msg;
	}
	
	private void checkEmail(String email) throws AddressException {
		int ix = email.indexOf('@');
		if (ix < 1) {
			throw new AddressException(email);
		}
		int ix2 = email.indexOf('@', ix + 1);
		if (ix2 >= 0) {
			int ixx = email.indexOf('<');
			if (ixx >= 0) {
				if (!(ix < ixx && ix2 > ixx)) {
					throw new AddressException(email);
				}
			} else {
				throw new AddressException(email);
			}
		}
	}
	
	public void processUploadAttachments(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		doUploadAttachments(request, response, out, false);
	}
	
	public void processUploadCids(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		doUploadAttachments(request, response, out, true);
	}
	
	private void doUploadAttachments(HttpServletRequest request, HttpServletResponse response, PrintWriter out, boolean cid) {
		try {
			checkStoreConnected();
			//WebTopApp webtopapp=environment.getWebTopApp();
			MultipartIterator iterator = new MultipartIterator(request, 64 * 1024, mss.getAttachmentMaxSize(), mss.getAttachDir());
			MultipartElement element = null;
			ArrayList<MultipartElement> elements = new ArrayList<MultipartElement>();
			int msgid = 0;
			while ((element = iterator.getNextElement()) != null) {
				if (element.isFile() && element.getFileName() != null && element.getFileName().length() > 0) {
					elements.add(element);
				} else {
					String name = element.getName();
					if (name.equals("newmsgid")) {
						msgid = Integer.parseInt(element.getValue());
					}
				}
			}
			if (msgid == 0) {
				String smsgid = request.getParameter("newmsgid");
				if (smsgid != null) {
					msgid = Integer.parseInt(smsgid);
				}
			}
			String sout = "{ success: true, data: [\n";
			boolean first = true;
			for (MultipartElement el : elements) {
				String filename = el.getFileName();
				File file = el.getFile();
				String ctype = "binary/octet-stream";
				int ix = filename.lastIndexOf(".");
				if (ix > 0) {
					String extension = filename.substring(ix + 1);
					String xctype = WT.getContentType(extension);
					if (xctype != null) {
						ctype = xctype;
					}
				}
				
				String cidname = null;
				if (cid) {
					cidname = filename;
				}
				Attachment attachment = attachFile(msgid, file, filename, ctype, cidname, false);
				String tempname = attachment.getFile().getName();
				if (!first) {
					sout += ",\n";
				}
				sout += "{ name: '" + OldUtils.jsEscape(filename) + "', tempname: '" + OldUtils.jsEscape(tempname) + "' }";
				first = false;
			}
			sout += "], result: null, id: 'id', jsonrpc: '2.0' }";
			out.println(sout);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processAttachFromMail(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String sout = null;
		try {
			checkStoreConnected();
            //WebTopApp webtopapp=environment.getWebTopApp();
			
			int msgid = Integer.parseInt(request.getParameter("newmsgid"));
			String pfoldername = request.getParameter("folder");
			String pidmessage = request.getParameter("idmessage");
			String pidattach = request.getParameter("idattach");
			
			FolderCache mcache = getFolderCache(pfoldername);
			int idmessage = Integer.parseInt(pidmessage);
			Message m = mcache.getMessage(idmessage);
			HTMLMailData mailData = mcache.getMailData((MimeMessage) m);
			Part part = mailData.getAttachmentPart(Integer.parseInt(pidattach));
			
			String ctype = part.getContentType();
			int ix = ctype.indexOf(";");
			if (ix > 0) {
				ctype = ctype.substring(0, ix);
			}
			
			String filename = part.getFileName();
			if (filename == null) {
				filename = "";
			}
			try {
				filename = OldUtils.decodeQString(filename);
			} catch (Exception exc) {
			}
			
			ix = filename.lastIndexOf(".");
			if (ix > 0) {
				String ext = filename.substring(ix + 1);
				String xctype = WT.getContentType(ext);
				if (xctype != null) {
					ctype = xctype;
				}
			}
			
			File file = File.createTempFile("strts", null, new File(environment.getCoreServiceSettings().getTempPath()));
			long size = file.length();
			createFile(part.getInputStream(), file);
			Attachment attachment = attachFile(msgid, file, filename, ctype, null, false);
			String tempname = attachment.getFile().getName();
			sout = "{ result:true, name: '" + OldUtils.jsEscape(filename) + "', tempname: '" + OldUtils.jsEscape(tempname) + "', size: '" + size + "' }";
		} catch (Exception exc) {
			exc.printStackTrace();
			sout = "{ result:false }";
		}
		out.println(sout);
	}

	// TODO: Cloud integration
/*    public void processUploadCloudAttachments(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 doUploadCloudAttachments(request, response, out);
	 }

	 private void doUploadCloudAttachments(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 try {
	 checkStoreConnected();
	 VFSService vfs=(VFSService)wts.getServiceByName("vfs");
	 FileObject rfo=vfs.getEmailsCloudFolder();
	 MultipartIterator iterator=new MultipartIterator(request,64*1024,vfs.getMaxInternalUploadSize(),rfo);
	 MultipartElement element=null; 
	 ArrayList<MultipartElement> elements=new ArrayList<MultipartElement>();
	 int msgid=0;
	 while((element=iterator.getNextElement())!=null) {
	 if (element.isFileObject() && element.getFileName()!=null && element.getFileName().length()>0) {
	 elements.add(element);
	 } else {
	 String name=element.getName();
	 if (name.equals("newmsgid")) {
	 msgid=Integer.parseInt(element.getValue());
	 }
	 }
	 }
	 if (msgid==0) {
	 String smsgid=request.getParameter("newmsgid");
	 if (smsgid!=null) msgid=Integer.parseInt(smsgid);
	 }
	 String sout="{ success: true, data: [\n";
	 boolean first=true;
	 for(MultipartElement el: elements) {
	 String filename=el.getFileName();
	 FileObject fileObject=el.getFileObject();
	 String vfsuri="0,"+vfs.getEmailsCloudFolderName()+"/"+filename;
                
	 String ctype="binary/octet-stream";
	 int ix=filename.lastIndexOf(".");
	 if (ix>0) {
	 String extension=filename.substring(ix+1);
	 String xctype=wta.getContentType(extension);
	 if (xctype!=null) ctype=xctype;
	 }

	 attachCloud(msgid,fileObject,filename,ctype,vfsuri);

	 if (!first) sout+=",\n";
	 sout+="{ name: '"+OldUtils.jsEscape(filename)+"', vfsuri: '"+OldUtils.jsEscape(vfsuri)+"' }";
                
	 first=false;
	 }
	 sout+="], result: null, id: 'id', jsonrpc: '2.0' }";
	 out.println(sout);
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 }
	 }

	 public void processRequestCloudFile(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String subject=request.getParameter("subject");
	 try {
	 checkStoreConnected();
	 VFSService vfs=(VFSService)wts.getServiceByName("vfs");
	 FileObject rfo=vfs.getUploadsCloudFolder();
	 java.util.Date now=new java.util.Date();
	 Calendar cal=Calendar.getInstance();
	 cal.setTime(now);
	 String dirname=cal.get(Calendar.YEAR)+"-"+(cal.get(Calendar.MONTH)+1)+"-"+cal.get(Calendar.DAY_OF_MONTH)+" "+cal.get(Calendar.HOUR)+":"+cal.get(Calendar.MINUTE)+":"+cal.get(Calendar.SECOND)+" - "+subject;
	 FileObject nfo=rfo.resolveFile(dirname);
	 nfo.createFolder();
	 String vfsuri="0,"+vfs.getUploadsCloudFolderName()+"/"+dirname;
	 String sout="{ success: true, vfsuri: '"+OldUtils.jsEscape(vfsuri)+"', name: '"+OldUtils.jsEscape(dirname)+"' }";
	 out.println(sout);
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 String sout="{ success: false, message: '"+exc.getMessage()+"' }";
	 out.println(sout);
	 }
	 }*/
	public void processSendReceipt(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		UserProfile profile = environment.getProfile();
		String subject = request.getParameter("subject");
		String to = request.getParameter("to");
		String folder = request.getParameter("folder");
		String sout = "";
		String from = profile.getCompleteEmailAddress();
		Identity ident = mprofile.getIdentity(folder);
		if (ident != null) {
			from = ident.displayname + " <" + ident.email + ">";
		}
		String body = "Il messaggio inviato a " + from + " con soggetto [" + subject + "] è stato letto.\n\n"
				+ "Your message sent to " + from + " with subject [" + subject + "] has been read.\n\n";
		try {
			checkStoreConnected();
			Exception exc = sendReceipt(from, to, subject, body);
			if (exc == null) {
				sout = "{\nresult: true\n}";
			} else {
				exc.printStackTrace();
				sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
			}
		} catch (MessagingException exc) {
			exc.printStackTrace();
			sout = "{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}";
		}
		out.println(sout);
	}

	// TODO: manage quick parts!!!!!
/*	public void processManageQuickParts(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String crud = null;
	 UserProfile profile = environment.getProfile();
	 SettingsManager sm = wta.getSettingsManager();
	 ArrayList<OServiceSetting> items = null;
		
	 try {
	 crud = ServletUtils.getStringParameter(request, "crud", true);
	 if(crud.equals(Crud.LIST)) {
	 items = sm.getUserSettings(profile, "mail", Settings.MESSAGE_QUICKPART+"%");
	 new JsonResult(JsQuickPart.asList(items)).printTo(out);
				
	 } else if(crud.equals(Crud.CREATE)) {
	 String id = ServletUtils.getStringParameter(request, "id", true);
	 String html = ServletUtils.getStringParameter(request, "html", true);
	 sm.setUserSetting(profile, "mail", Settings.MESSAGE_QUICKPART+"@"+id, html);
				
	 items = sm.getUserSettings(profile, "mail", Settings.MESSAGE_QUICKPART+"%");
	 new JsonResult(JsQuickPart.asList(items)).printTo(out);
				
	 } else if(crud.equals(Crud.DELETE)) {
	 String id = JsonResult.gson.fromJson(request.getParameter("data"), String.class); // Data contains key field defined in grid
	 sm.deleteUserSetting(profile, "mail", Settings.MESSAGE_QUICKPART+"@"+id);
				
	 new JsonResult().printTo(out);
	 }
	 } catch (Exception ex) {
	 logger.error("Error managing quickparts", ex);
	 new JsonResult(false, "Error managing quickparts").printTo(out);
	 }
	 }*/
	// TODO: manage mailcard !!!!
/*	public void processManageMailcard(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile = environment.getProfile();
	 ProfilePersonalInfo ppi = null;
	 String crud = null;
	 Mailcard mc = null;
	 JsMailcard jsmc = null;
		
	 try {
	 crud = ServletOldUtils.getStringParameter(request, "crud", true);
	 String id = ServletUtils.getStringParameter(request, "id", true);
			
	 if(crud.equals(Crud.READ)) {
	 if(id.equals("emaildomain")) {
	 mc = wtd.getEmailDomainMailcard(profile);
					
	 } else if(StringUtils.startsWith(id, "identity")) {
	 String[] tokens = StringUtils.split(id, '|');
	 int index = Integer.parseInt(tokens[1]);
	 if(index == 0) {
	 mc = wtd.getMailcard(profile);
	 } else {
	 Identity ide = profile.getIdentities().get(index-1);
	 mc = wtd.getMailcard(ide.email);
	 }
	 }
	 new JsonResult(new JsMailcard(id, mc)).printTo(out);
				
	 } else if(crud.equals(Crud.UPDATE)) {
	 String html = ServletUtils.getStringParameter(request, "html", true);
	 if(id.equals("emaildomain")) {
	 wtd.setEmailDomainMailcard(profile.getEmailAddress(), html);
	 mc = wtd.getEmailDomainMailcard(profile);
					
	 } else if(StringUtils.startsWith(id, "identity")) {
	 String[] tokens = StringUtils.split(id, '|');
	 int index = Integer.parseInt(tokens[1]);
	 if(index == 0) {
	 String target = ServletUtils.getStringParameter(request, "target", true);
	 if(target.equals(Mailcard.TYPE_EMAIL)) {
	 wtd.setEmailMailcard(profile.getEmailAddress(), html);
	 wtd.setUserMailcard(profile.getUser(), null);
	 } else {
	 wtd.setUserMailcard(profile.getUser(), html);
	 wtd.setEmailMailcard(profile.getEmailAddress(), null);
	 }
	 mc = wtd.getMailcard(profile);
	 ppi = profile.getPersonalInfo();
	 } else {
	 Identity ide = profile.getIdentities().get(index-1);
	 wtd.setEmailMailcard(ide.email, html);
	 mc = wtd.getMailcard(ide.email);
	 ppi = getPersonalInfo(ide);
	 }
	 mc.substitutePlaceholders(ppi);
	 }
	 new JsonResult(new JsMailcard(id, mc)).printTo(out);
					
	 } else if(crud.equals(Crud.DELETE)) {
	 if(id.equals("emaildomain")) {
	 wtd.setEmailDomainMailcard(profile.getEmailAddress(), null);
	 mc = wtd.getEmailDomainMailcard(profile);
					
	 } else if(StringUtils.startsWith(id, "identity")) {
	 String[] tokens = StringUtils.split(id, '|');
	 int index = Integer.parseInt(tokens[1]);
	 if(index == 0) {
	 wtd.setEmailMailcard(profile.getEmailAddress(), null);
	 wtd.setUserMailcard(profile.getUser(), null);
	 mc = wtd.getMailcard(profile);
	 ppi = profile.getPersonalInfo();
	 } else {
	 Identity ide = profile.getIdentities().get(index-1);
	 wtd.setEmailMailcard(ide.email, null);
	 mc = wtd.getMailcard(ide.email);
	 ppi = getPersonalInfo(ide);
	 }
	 mc.substitutePlaceholders(ppi);
	 }
	 new JsonResult(new JsMailcard(id, mc)).printTo(out);
	 }
	 } catch (Exception ex) {
	 logger.error("Error managing mailcard.", ex);
	 new JsonResult(false, "Unable to manage mailcard.").printTo(out);
	 }
	 }
	
	 private ProfilePersonalInfo getPersonalInfo(Identity identity) {
	 Connection con = null;
	 try {
	 con = wta.getMainConnection();
	 UserProfile up = environment.getUserProfile();
	 logger.debug("Getting profile info by identity. [{}, {}]", identity.type, identity.email);
	 if(identity.type.equals(Identity.TYPE_AUTO)) {
	 // Within AUTO type, identity belongs from a specific share 
	 // initiated by a user. We have to evaluate the read-only 
	 // property in order to force the sharing user's profile info.
	 // In other cases, current profile info is returned back.
	 ArrayList<OWorkgroup> wgs = WebTopDb.selectWorkgroupByDomainEmailLogin(con, up.getIDDomain(), identity.email, up.getUser());
	 if(wgs.size() == 1) {
	 OWorkgroup wg = wgs.get(0);
	 if(wg.mail.equals("R")) {
	 logger.debug("Forcing [{}] personal info.", wg.groupname);
	 ProfileDataProviderBase pdp = wta.getProfileDataProvider(up.getIDDomain());
	 return pdp.getPersonalInfo(wg.groupname);
	 }
	 }
	 logger.debug("Returning my profile info.");
	 return up.getPersonalInfo();
				
	 } else if(identity.type.equals(Identity.TYPE_USER)) {
	 // Within USER type, we have to look for a user using his email
	 // address (that in identity list). If more than one user is
	 // found we return back current profile info.
	 ArrayList<OUser> uss = WebTopDb.selectUserByDomainEmail(con, up.getIDDomain(), identity.email);
	 if(uss.size() == 1) {
	 OUser us = uss.get(0);
	 logger.debug("User found! Applying [{}] personal info.", us.login);
	 ProfileDataProviderBase pdp = wta.getProfileDataProvider(up.getIDDomain());
	 return pdp.getPersonalInfo(us.login);
	 } else if(uss.size() > 1) {
	 logger.debug("Many users found! Returning my profile info.");
	 } else {
	 logger.debug("Returning my profile info.");
	 }
	 return up.getPersonalInfo();
				
	 } else {
	 return null;
	 }
	 } catch(Exception ex) {
	 logger.error("Unable to determine profile's personal info.", ex);
	 return null;
	 } finally {
			
	 }
	 }*/
	// TODO: get settings!!!!!!!!!!!
/*    public void processGetSettings(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getUserProfile();
	 AuthenticationDomain ad=environment.getWebTopDomain().getAuthenticationDomain();
	 SettingsManager sm = wta.getSettingsManager();
	 String authprot=ad.getAuthUriProtocol();
	 Mailcard mc = null;

	 String sout="{\n";
	 mc = wtd.getEmailDomainMailcard(profile);
	 sout += " mailcardsource: '"+mc.source+"',";
	 sout += " mailcard: '"+OldUtils.jsEscape(mc.html)+"',";
	 ArrayList<Identity> identities=profile.getIdentities();
	 mc = wtd.getMailcard(profile);
	 mc.substitutePlaceholders(profile.getPersonalInfo());
	 sout+=" identities: [ \n";
	 sout+="    { "+
	 " type: '"+Identity.TYPE_AUTO+"',"+
	 " displayname: '"+OldUtils.jsEscape(profile.getDisplayName())+"',"+
	 " email: '"+OldUtils.jsEscape(profile.getEmailAddress())+"',"+
	 " mailcardsource: '"+mc.source+"',"+
	 " mailcard: '"+OldUtils.jsEscape(mc.html)+"',"+
	 " folder: null"+
	 "    }";
	 for(Identity i: identities) {
	 sout+=",\n    { "+
	 " type: '"+i.type+"',"+
	 " displayname: '"+OldUtils.jsEscape(i.displayname)+"',"+
	 " email: '"+OldUtils.jsEscape(i.email)+"',";
	 mc=wtd.getMailcard(i);
	 if (mc!=null) {
	 mc.substitutePlaceholders(getPersonalInfo(i));
	 sout+=" mailcardsource: '"+mc.source+"',";
	 sout+=" mailcard: '"+OldUtils.jsEscape(mc.html)+"',";
	 }
	 sout+=" folder: "+(i.mainfolder==null?"null":"'"+OldUtils.jsEscape(i.mainfolder)+"'")+
	 "    }";
	 }
	 char sep='.';
	 try { sep=getFolderSeparator(); } catch(Exception exc) {}
	 sout+=" ],\n";
        
	 sout+=" columnsize: {\n";
	 sout+="    folder: "+wts.getServiceSetting("mail", "column-folder", "100")+",\n";
	 sout+="    folderdesc: "+wts.getServiceSetting("mail", "column-folderdesc", "100")+",\n";
	 sout+="    priority: "+wts.getServiceSetting("mail", "column-priority", "35")+",\n";
	 sout+="    status: "+wts.getServiceSetting("mail", "column-status", "30")+",\n";
	 sout+="    from: "+wts.getServiceSetting("mail", "column-from", "200")+",\n";
	 sout+="    to: "+wts.getServiceSetting("mail", "column-to", "200")+",\n";
	 sout+="    subject: "+wts.getServiceSetting("mail", "column-subject", "400")+",\n";
	 sout+="    date: "+wts.getServiceSetting("mail", "column-date", "80")+",\n";
	 sout+="    size: "+wts.getServiceSetting("mail", "column-size", "50")+",\n";
	 sout+="    flag: "+wts.getServiceSetting("mail", "column-flag", "30")+",\n";
	 sout+="    arch: "+wts.getServiceSetting("mail", "column-arch", "30")+"\n";
	 sout+=" },\n";
        
	 Connection con=null;
	 Statement stmt=null;
	 ResultSet rs=null;
	 String vactive="N";
	 String vmessage="";
	 String vaddresses=profile.getEmailAddress();
	 try {
	 String sql="select * from vacation where iddomain='"+profile.getIDDomain()+"' and login='"+profile.getUser()+"'";
	 con=wtd.getConnection();
	 stmt=con.createStatement();
	 rs=stmt.executeQuery(sql);
	 if (rs.next()) {
	 vactive=rs.getString("active");
	 vmessage=rs.getString("message");
	 vaddresses=rs.getString("addresses");
	 }
	 rs.close();
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 } finally {
	 if (rs!=null) try { rs.close(); } catch(Exception exc) {}
	 if (stmt!=null) try { stmt.close(); } catch(Exception exc) {}
	 if (con!=null) try { con.close(); } catch(Exception exc) {}
	 }
	 String imapPassword=profile.getMailPassword();
	 String imapPassFake="";
	 if (imapPassword!=null) for(int i=0;i<imapPassword.length();++i) imapPassFake+="x";
        
        
	 sout+="  vactive: "+(vactive.equals("Y")?"true":"false")+",\n";
	 sout+="  vmessage: '"+OldUtils.jsEscape(vmessage)+"',\n";
	 sout+="  vaddresses: '"+OldUtils.jsEscape(vaddresses)+"',\n";
	 sout+="  maxattachsize: '"+wtd.getMaxAttachmentSize()+"',\n";
	 sout+="  fontface: '"+OldUtils.jsEscape(profile.getFontFace())+"',\n";
	 sout+="  fontsize: '"+OldUtils.jsEscape(profile.getFontSize())+"',\n";
	 sout+="  folderSent: '"+OldUtils.jsEscape(profile.getFolderSent())+"',\n";
	 sout+="  folderTrash: '"+OldUtils.jsEscape(profile.getFolderTrash())+"',\n";
	 sout+="  folderSpam: '"+OldUtils.jsEscape(profile.getFolderSpam())+"',\n";
	 sout+="  sharedsort: '"+OldUtils.jsEscape(profile.getMailSharedSort())+"',\n";
	 sout+="  folderDrafts: '"+OldUtils.jsEscape(profile.getFolderDrafts())+"',\n";
	 sout+="  folderPrefix: '"+OldUtils.jsEscape(folderPrefix)+"',\n";
	 sout+="  mailProtocol: '"+OldUtils.jsEscape(profile.getMailProtocol())+"',\n";
	 sout+="  mailHost: '"+OldUtils.jsEscape(profile.getMailHost())+"',\n";
	 sout+="  mailPort: '"+profile.getMailPort()+"',\n";
	 sout+="  mailUser: '"+OldUtils.jsEscape(profile.getMailUsername())+"',\n";
	 sout+="  mailPassword: '"+OldUtils.jsEscape(imapPassFake)+"',\n";
	 sout+="  separator: '"+sep+"',\n";
	 sout+="  askreceipt: "+profile.isAskReceipt()+",\n";
	 sout+="  scanall: "+profile.isScanAll()+",\n";
	 sout+="  sharedseen: "+sm.getUserSetting(profile, "mail", Settings.SHARED_SEEN)+",\n";
	 sout+="  scansecs: "+profile.getScanSeconds()+",\n";
	 sout+="  scancycles: "+profile.getScanCycles()+",\n";
	 sout+="  optimap: "+(profile.isAdministrator()||(!profile.isAutoMailSettings()))+",\n";
	 sout+="  optimap2: "+(profile.isAdministrator()||(!authprot.equals("webtop")))+",\n";
	 sout+="  pageRows: "+profile.getNumMsgList()+",\n";
	 sout+="  viewHPerc: "+wts.getServiceSetting("mail", "pane-hperc", "60");        
	 sout+="}";
	 out.println(sout);
	 }*/
	HashMap<String, MessageListThread> mlThreads = new HashMap<String, MessageListThread>();

	// TODO: groupChanged !!!!
/*	public void processGroupChanged(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String group=request.getParameter("group");
	 String folder=request.getParameter("folder");
	 UserProfile profile=environment.getProfile();
	 wts.setServiceSetting("mail", "messagelist-group-"+folder,group);
	 if (!group.equals("")) wts.setServiceSetting("mail", "messagelist-"+folder+"-sort", "date|DESC");
	 new JsonResult(true,"").printTo(out);
	 }*/
	public void processListMessages(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		UserProfile profile = environment.getProfile();
		Locale locale = profile.getLocale();
		Calendar cal = Calendar.getInstance(locale);
		String pfoldername = request.getParameter("folder");
		String psortfield = request.getParameter("sort");
		String psortdir = request.getParameter("dir");
		String pstart = request.getParameter("start");
		String plimit = request.getParameter("limit");
		String ppage = request.getParameter("page");
		String psearchfield = request.getParameter("searchfield");
		String ppattern = request.getParameter("pattern");
		String prefresh = request.getParameter("refresh");
		if (psearchfield != null && psearchfield.trim().length() == 0) {
			psearchfield = null;
		}
		if (ppattern != null && ppattern.trim().length() == 0) {
			ppattern = null;
		}
		boolean refresh = (prefresh != null && prefresh.equals("true"));
		
		String group = mus.getMessageListGroup(pfoldername);
		if (group == null) {
			group = "";
		}

		//System.out.println("psortfield="+psortfield+" group="+group);
		if (psortfield == null) {
			if (group.equals("")) {
				String s = mus.getMessageListSort(pfoldername);
				int ix = s.indexOf("|");
				psortfield = s.substring(0, ix);
				psortdir = s.substring(ix + 1);
			} else {
				psortfield = "date";
				psortdir = "DESC";
			}
		} else {
			group = "";
			// TODO: save new sort/group
			//wts.setServiceSetting("mail", "messagelist-"+pfoldername+"-sort", psortfield+"|"+psortdir);
			//wts.setServiceSetting("mail", "messagelist-group-"+pfoldername,"");
		}

        //if (psortfield==null) psortfield="date";
		//if (psortdir==null) psortdir="DESC";
		int sortby = MessageComparator.SORT_BY_NONE;
		if (psortfield.equals("messageid")) {
			sortby = MessageComparator.SORT_BY_MSGIDX;
		} else if (psortfield.equals("date")) {
			sortby = MessageComparator.SORT_BY_DATE;
		} else if (psortfield.equals("priority")) {
			sortby = MessageComparator.SORT_BY_PRIORITY;
		} else if (psortfield.equals("to")) {
			sortby = MessageComparator.SORT_BY_RCPT;
		} else if (psortfield.equals("from")) {
			sortby = MessageComparator.SORT_BY_SENDER;
		} else if (psortfield.equals("size")) {
			sortby = MessageComparator.SORT_BY_SIZE;
		} else if (psortfield.equals("subject")) {
			sortby = MessageComparator.SORT_BY_SUBJECT;
		} else if (psortfield.equals("status")) {
			sortby = MessageComparator.SORT_BY_STATUS;
		} else if (psortfield.equals("flag")) {
			sortby = MessageComparator.SORT_BY_FLAG;
		}
		boolean ascending = psortdir.equals("ASC");
		
		int sort_group = MessageComparator.SORT_BY_NONE;
		boolean groupascending = true;
		if (group.equals("messageid")) {
			sort_group = MessageComparator.SORT_BY_MSGIDX;
		} else if (group.equals("gdate")) {
			sort_group = MessageComparator.SORT_BY_DATE;
			groupascending = false;
		} else if (group.equals("priority")) {
			sort_group = MessageComparator.SORT_BY_PRIORITY;
		} else if (group.equals("to")) {
			sort_group = MessageComparator.SORT_BY_RCPT;
		} else if (group.equals("from")) {
			sort_group = MessageComparator.SORT_BY_SENDER;
		} else if (group.equals("size")) {
			sort_group = MessageComparator.SORT_BY_SIZE;
		} else if (group.equals("subject")) {
			sort_group = MessageComparator.SORT_BY_SUBJECT;
		} else if (group.equals("status")) {
			sort_group = MessageComparator.SORT_BY_STATUS;
		} else if (group.equals("flag")) {
			sort_group = MessageComparator.SORT_BY_FLAG;
		}

		//Save search requests
		if (psearchfield != null && psearchfield.trim().length() > 0 && ppattern != null && ppattern.trim().length() > 0) {
			// TODO: save filter!!!!
			//wts.setServiceStoreEntry(getName(), "filter"+psearchfield, ppattern.toUpperCase(),ppattern);
		}
		
		int start = 0;
		int limit = mprofile.getNumMsgList();
		if (ppage==null) {
			if (pstart != null) {
				start = Integer.parseInt(pstart);
			}
			if (plimit != null) {
				limit = Integer.parseInt(plimit);
			}
		} else {
			int page=Integer.parseInt(ppage);
			int nxpage=mprofile.getNumMsgList();
			start=(page-1)*nxpage;
			limit=nxpage;
		}
		
		String sout = "{\n";
		Folder folder = null;
		try {
			checkStoreConnected();
			int funread = 0;
			if (pfoldername == null) {
				folder = getDefaultFolder();
			} else {
				folder = getFolder(pfoldername);
			}
			boolean issent = isSentFolder(folder.getFullName());
			boolean isdrafts = isDraftsFolder(folder.getFullName());
			if (!issent) {
				String names[] = folder.getFullName().split("\\" + getFolderSeparator());
				for (String pname : names) {
					if (isSentFolder(pname)) {
						issent = true;
						break;
					}
				}
			}
			
			String ctn = Thread.currentThread().getName();
			String key = folder.getFullName();
			FolderCache mcache = getFolderCache(key);
			//Message msgs[]=mcache.getMessages(ppattern,psearchfield,sortby,ascending,refresh);
			if (ppattern != null && psearchfield != null) {
				key += "|" + ppattern + "|" + psearchfield;
			}
			MessageListThread mlt = mlThreads.get(key);
			if (mlt == null) {
				mlThreads.put(key, mlt = new MessageListThread(mcache, ppattern, psearchfield, sortby, ascending, refresh, sort_group, groupascending));
			}
			String tname = Thread.currentThread().getName();			
			long millis = System.currentTimeMillis();
			mlt.lastRequest = millis;
			synchronized (mlt.lock) {
				if (!mlt.started) {
					Thread t = new Thread(mlt);
					t.start();
				}
				if (!mlt.finished) {
					try {
						mlt.lock.wait();
					} catch (InterruptedException exc) {
						exc.printStackTrace();
					}
					mlThreads.remove(key);
				}
			}
			Message msgs[] = null;
			if (mlt.lastRequest == millis) {
				msgs = mlt.msgs;
			}
			
			if (msgs != null) {
				
				sout += "total:" + msgs.length + ",\nstart:" + start + ",\nlimit:" + limit + ",\nmessages: [\n";

				/*               if (ppattern==null && !isSpecialFolder(mcache.getFolderName())) {
				 //mcache.fetch(msgs,FolderCache.flagsFP,0,start);
				 for(int i=0;i<start;++i) {
				 try {
				 if (!msgs[i].isSet(Flags.Flag.SEEN)) funread++;
				 } catch(Exception exc) {

				 }
				 }
				 }*/
				int max = start + limit;
				if (max > msgs.length) {
					max = msgs.length;
				}
				//Fetch others for these messages
				mcache.fetch(msgs, FP, start, max);
				for (int i = 0, ni = 0; i < limit; ++ni, ++i) {
					int ix = start + i;
					int nx = start + ni;
					if (nx >= msgs.length) {
						break;
					}
					if (ix >= max) {
						break;
					}
					Message xm = msgs[nx];
					if (xm.isExpunged()) {
						--i;
						continue;
					}
					/*String ids[]=null;
					 try {
					 ids=xm.getHeader("Message-ID");
					 } catch(MessagingException exc) {
					 --i;
					 continue;
					 }
					 if (ids==null || ids.length==0) { --i; continue; }
					 String idmessage=ids[0];*/
					int nid = xm.getMessageNumber();
					IMAPMessage m = (IMAPMessage) xm;
					//Date
					java.util.Date d = m.getSentDate();
					if (d == null) {
						d = m.getReceivedDate();
					}
					if (d == null) {
						d = new java.util.Date(0);
					}
					cal.setTime(d);
					int yyyy = cal.get(Calendar.YEAR);
					int mm = cal.get(Calendar.MONTH);
					int dd = cal.get(Calendar.DAY_OF_MONTH);
					int hhh = cal.get(Calendar.HOUR_OF_DAY);
					int mmm = cal.get(Calendar.MINUTE);
					int sss = cal.get(Calendar.SECOND);
					//From
					String from = "";
					Address ia[] = m.getFrom();
					if (ia != null) {
						InternetAddress iafrom = (InternetAddress) ia[0];
						from = iafrom.getPersonal();
						if (from == null) {
							from = iafrom.getAddress();
						}
					}
					from = (from == null ? "" : OldUtils.jsEscape(OldUtils.htmlescape(from)));
					//To
					String to = "";
					ia = m.getRecipients(Message.RecipientType.TO);
					if (ia != null) {
						InternetAddress iato = (InternetAddress) ia[0];
						to = iato.getPersonal();
						if (to == null) {
							to = iato.getAddress();
						}
					}
					to = (to == null ? "" : OldUtils.jsEscape(OldUtils.htmlescape(to)));
					//Subject
					String subject = m.getSubject();
					if (subject != null) {
						try {
							subject = OldUtils.decodeQString(subject);
						} catch (Exception exc) {
							
						}
					}
					boolean hasAttachments = hasAttachements(m);
					subject = (subject == null ? "" : OldUtils.jsEscape(OldUtils.htmlescape(subject)));
					//Unread
					boolean unread = !m.isSet(Flags.Flag.SEEN);
					if (ppattern == null && unread) {
						++funread;
					}
					//Priority
					int priority = getPriority(m);
					//Status
					java.util.Date today = new java.util.Date();
					Calendar cal1 = Calendar.getInstance(locale);
					Calendar cal2 = Calendar.getInstance(locale);
					boolean isToday = false;
					String gdate = "";
					String sdate = "";
					String xdate = "";
					if (d != null) {
						cal1.setTime(today);
						cal2.setTime(d);
						
						gdate = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(d);
						sdate=cal2.get(Calendar.YEAR)+"/"+String.format("%02d",(cal2.get(Calendar.MONTH)+1))+"/"+String.format("%02d",cal2.get(Calendar.DATE));
						//boolean isGdate = group.equals("gdate");
						if (cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) && cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) {
							int dx = cal1.get(Calendar.DAY_OF_MONTH) - cal2.get(Calendar.DAY_OF_MONTH);
							if (dx == 0) {
								isToday = true;
								//if (isGdate) {
									xdate = WT.lookupCoreResource(locale, CoreLocaleKey.DATE_TODAY);
								//}
							} else if (dx == 1 /*&& isGdate*/) {
								xdate = WT.lookupCoreResource(locale, CoreLocaleKey.DATE_YESTERDAY);
							}
						}
					}
					
					Flags flags = m.getFlags();
					String status = "read";
					if (flags != null) {
						if (flags.contains(Flags.Flag.ANSWERED)) {
							if (flags.contains("$Forwarded")) {
								status = "repfwd";
							} else {
								status = "replied";
							}
						} else if (flags.contains("$Forwarded")) {
							status = "forwarded";
						} else if (flags.contains(Flags.Flag.SEEN)) {
							status = "read";
						} else if (isToday) {
							status = "new";
						} else {
							status = "unread";
						}
						//                    if (flags.contains(Flags.Flag.USER)) flagImage=webtopapp.getUri()+"/images/themes/"+profile.getTheme()+"/mail/flag.gif";
					}
					//Size
					int msgsize = 0;
					msgsize = (m.getSize() * 3) / 4;// /1024 + 1;
					//User flags
					String cflag = "";
					for (String flagstring : Service.flagStrings) {
						if (!flagstring.equals("complete")) {
							String oldflagstring = "flag" + flagstring;
							if (flags.contains(flagstring)) {
								cflag = flagstring;
							} else if (flags.contains(oldflagstring)) { // vecchio modo
								cflag = flagstring;
							}
						}
					}
					boolean flagComplete = flags.contains("complete");
					if (flagComplete) {
						if (cflag.length() > 0) {
							cflag += "-complete";
						} else {
							cflag = "complete";
						}
					}
					boolean hasNote = flags.contains("mailnote");
					
					boolean issched = false;
					int syyyy = 0;
					int smm = 0;
					int sdd = 0;
					int shhh = 0;
					int smmm = 0;
					int ssss = 0;
					if (isdrafts) {
						String h = getSingleHeaderValue(m, "Sonicle-send-scheduled");
						if (h != null && h.equals("true")) {
							Calendar scal = parseScheduleHeader(getSingleHeaderValue(m, "Sonicle-send-date"), getSingleHeaderValue(m, "Sonicle-send-time"));
							syyyy = scal.get(Calendar.YEAR);
							smm = scal.get(Calendar.MONTH);
							sdd = scal.get(Calendar.DAY_OF_MONTH);
							shhh = scal.get(Calendar.HOUR_OF_DAY);
							smmm = scal.get(Calendar.MINUTE);
							ssss = scal.get(Calendar.SECOND);
							issched = true;
							status = "scheduled";
						}						
					}

                    //idmessage=idmessage.replaceAll("\\\\", "\\\\");
					//idmessage=OldUtils.jsEscape(idmessage);
					if (i > 0) {
						sout += ",\n";
					}
					boolean archived = false;
					if (profile.hasDocumentManagement()) {
						archived = m.getHeader("X-WT-Archived") != null;
					}
					sout += "{idmessage:'" + nid + "',"
							+ "priority:" + priority + ","
							+ "status:'" + status + "',"
							+ "to:'" + to + "',"
							+ "from:'" + from + "',"
							+ "subject:'" + subject + "',"
							+ "date: new Date(" + yyyy + "," + mm + "," + dd + "," + hhh + "," + mmm + "," + sss + "),"
							+ "gdate: '" + gdate + "',"
							+ "sdate: '" + sdate + "',"
							+ "xdate: '" + xdate + "',"
							+ "unread: " + unread + ","
							+ "size:" + msgsize + ","
							+ "flag:'" + cflag + "'"
							+ (hasNote ? ",note:true" : "")
							+ (archived ? ",arch:true" : "")
							+ (isToday ? ",istoday:true" : "")
							+ (hasAttachments ? ",atts:true" : "")
							+ (issched ? ",scheddate: new Date(" + syyyy + "," + smm + "," + sdd + "," + shhh + "," + smmm + "," + ssss + ")" : "")
							+ "}";
					//                sout+="{messageid:'"+m.getMessageID()+"',from:'"+from+"',subject:'"+subject+"',date: new Date("+yyyy+","+mm+","+dd+"),unread: "+unread+"},\n";
				}
				/*                if (ppattern==null && !isSpecialFolder(mcache.getFolderName())) {
				 //if (max<msgs.length) mcache.fetch(msgs,FolderCache.flagsFP,max,msgs.length);
				 for(int i=max;i<msgs.length;++i) {
				 try {
				 if (!msgs[i].isSet(Flags.Flag.SEEN)) funread++;
				 } catch(Exception exc) {

				 }
				 }
				 } else {
				 funread=mcache.getUnreadMessagesCount();
				 }*/
				if (!mcache.isSpecial()) {
					mcache.refreshUnreads();
				}
				funread = mcache.getUnreadMessagesCount();
				sout += "\n],\n";
				sout += "metaData: {\n"
						+ "  root: 'messages', total: 'totalCount', idProperty: 'idmessage',\n"
						+ "  fields: ['idmessage','priority','status','to','from','subject','date','gdate','unread','size','flag','note','arch','istoday','atts','scheddate'],\n"
						+ "  sortInfo: { field: '" + psortfield + "', direction: '" + psortdir + "' },\n"
						+ "  groupField: '" + group + "',\n";
				
				ColumnVisibilitySetting cvs = mus.getColumnVisibilitySetting(pfoldername);
				// Apply grid defaults
				ColumnVisibilitySetting.applyDefaults(mcache.isSent(), cvs);

				// Fills columnsInfo object for client rendering
				sout += "colsInfo2: [";
				for (String dataIndex : cvs.keySet()) {
					sout += "{dataIndex:'" + dataIndex + "',hidden:" + String.valueOf(!cvs.get(dataIndex)) + "},";
				}
				if (StringUtils.right(sout, 1).equals(",")) {
					sout = StringUtils.left(sout, sout.length() - 1);
				}
				sout += "]\n";
				
				sout += "},\n";
				sout += "unread: " + funread + ", issent: " + issent + ", millis: " + mlt.millis + " }\n";
			} else {
				sout += "total:0,\nstart:0,\nlimit:0,\nmessages: [\n";
				sout += "\n], unread: 0, issent: false }\n";
			}
			out.println(sout);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	private String getSingleHeaderValue(Message m, String headerName) throws MessagingException {
		String s[] = m.getHeader(headerName);
		String sv = null;
		if (s != null && s.length > 0) {
			sv = s[0];
		}
		return sv;
	}
	
	private Calendar parseScheduleHeader(String senddate, String sendtime) {
		String sdp[] = senddate.split("/");
		String sdt[] = sendtime.split(":");
		String sschedday = sdp[0];
		String sschedmonth = sdp[1];
		String sschedyear = sdp[2];
		String sschedhour = sdt[0];
		String sschedmins = sdt[1];
		int schedday = Integer.parseInt(sschedday);
		int schedmonth = Integer.parseInt(sschedmonth);
		int schedyear = Integer.parseInt(sschedyear);
		int schedhour = Integer.parseInt(sschedhour);
		int schedmins = Integer.parseInt(sschedmins);
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.YEAR, schedyear);
		cal.set(Calendar.MONTH, schedmonth - 1);
		cal.set(Calendar.DATE, schedday);
		cal.set(Calendar.HOUR_OF_DAY, schedhour);
		cal.set(Calendar.MINUTE, schedmins);
		return cal;
	}
	
	private boolean isAttachment(Part part) throws MessagingException {
		return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
				|| Part.INLINE.equalsIgnoreCase(part.getDisposition())
				|| (part.getDisposition() == null && part.getFileName() != null);
	}
	
	private boolean hasAttachements(Part p) throws MessagingException, IOException {
		boolean retval = false;

		//String disp=p.getDisposition();
		if (isAttachment(p)) {
			retval = true;
		} //if (disp!=null && disp.equalsIgnoreCase(Part.ATTACHMENT)) retval=true;
		else if (p.isMimeType("multipart/*")) {
			Multipart mp = (Multipart) p.getContent();
			int parts = mp.getCount();
			for (int i = 0; i < parts; ++i) {
				Part bp = mp.getBodyPart(i);
				if (hasAttachements(bp)) {
					retval = true;
					break;
				}
			}
		}
		
		return retval;
	}
	
	class MessageListThread implements Runnable {
		
		FolderCache fc;
		String pattern;
		String searchfield;
		int sortby;
		boolean ascending;
		int sort_group;
		boolean groupascending;
		boolean refresh;
		long millis;
		
		Message msgs[] = null;
		boolean started = false;
		boolean finished = false;
		final Object lock = new Object();
		long lastRequest = 0;
		
		MessageListThread(FolderCache fc, String pattern, String searchfield, int sortby, boolean ascending, boolean refresh, int sort_group, boolean groupascending) {
			this.fc = fc;
			this.pattern = pattern;
			this.searchfield = searchfield;
			this.sortby = sortby;
			this.ascending = ascending;
			this.refresh = refresh;
			this.sort_group = sort_group;
			this.groupascending = groupascending;
		}
		
		public void run() {
			started = true;
			finished = false;
			synchronized (lock) {
				try {
					this.millis = System.currentTimeMillis();
					msgs = fc.getMessages(pattern, searchfield, sortby, ascending, refresh, sort_group, groupascending);
				} catch (Exception exc) {
					exc.printStackTrace();
				}
				lock.notifyAll();
			}
			finished = true;
		}
	}
	
	DateFormat df = null;
	
	public void processGetMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String pidattach = request.getParameter("idattach");
		String providername = request.getParameter("provider");
		String providerid = request.getParameter("providerid");
		int idattach = 0;
		boolean isEditor = request.getParameter("editor") != null;
		if (df == null) {
			df = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM, environment.getProfile().getLocale());
		}
		try {
			FolderCache mcache = null;
			Message m = null;
			int recs = 0;
			int msgnum = -1;
			String vheader[] = null;
			boolean isseen = false;
			String sout = "{\nmessage: [\n";
			if (providername == null) {
				checkStoreConnected();
				mcache = getFolderCache(pfoldername);
				msgnum = Integer.parseInt(pidmessage);
				m = mcache.getMessage(msgnum);
				if (m.isExpunged()) {
					throw new MessagingException("Message " + pidmessage + " expunged");
				}
				vheader = m.getHeader("Disposition-Notification-To");
				isseen = m.isSet(Flags.Flag.SEEN);
				
				if (pidattach != null) {
					
					HTMLMailData mailData = mcache.getMailData((MimeMessage) m);
					Part part = mailData.getAttachmentPart(Integer.parseInt(pidattach));
					m = (Message) part.getContent();
					idattach = Integer.parseInt(pidattach) + 1;
				}
			} else {
				// TODO: provider get message!!!!
/*                WebTopService provider=wts.getServiceByName(providername);
				 MessageContentProvider mcp=provider.getMessageContentProvider(providerid);
				 m=new MimeMessage(session,mcp.getSource());
				 mcache=fcProvided;
				 mcache.addProvidedMessage(providername, providerid, m);*/
			}
			String subject = m.getSubject();
			if (subject == null) {
				subject = "";
			} else {
				try {
					subject = OldUtils.decodeQString(subject);
				} catch (Exception exc) {
					
				}
			}
			java.util.Date d = m.getSentDate();
			if (d == null) {
				d = m.getReceivedDate();
			}
			if (d == null) {
				d = new java.util.Date(0);
			}
			String date = df.format(d).replaceAll("\\.", ":");
			String fromName = "";
			String fromEmail = "";
			Address as[] = m.getFrom();
			if (as != null && as.length > 0) {
				InternetAddress ia = (InternetAddress) as[0];
				fromName = ia.getPersonal();
				fromEmail = adjustEmail(ia.getAddress());
				if (fromName == null) {
					fromName = fromEmail;
				}
			}
			sout += "{iddata:'from',value1:'" + OldUtils.jsEscape(OldUtils.htmlescape(fromName)) + "',value2:'" + OldUtils.jsEscape(fromEmail) + "',value3:0},\n";
			recs += 2;
			Address tos[] = m.getRecipients(RecipientType.TO);
			if (tos != null) {
				for (Address to : tos) {
					InternetAddress ia = (InternetAddress) to;
					String toName = ia.getPersonal();
					String toEmail = adjustEmail(ia.getAddress());
					if (toName == null) {
						toName = toEmail;
					}
					sout += "{iddata:'to',value1:'" + OldUtils.jsEscape(OldUtils.htmlescape(toName)) + "',value2:'" + OldUtils.jsEscape(toEmail) + "',value3:0},\n";
					++recs;
				}
			}
			Address ccs[] = m.getRecipients(RecipientType.CC);
			if (ccs != null) {
				for (Address cc : ccs) {
					InternetAddress ia = (InternetAddress) cc;
					String ccName = ia.getPersonal();
					String ccEmail = adjustEmail(ia.getAddress());
					if (ccName == null) {
						ccName = ccEmail;
					}
					sout += "{iddata:'cc',value1:'" + OldUtils.jsEscape(ccName) + "',value2:'" + OldUtils.jsEscape(ccEmail) + "',value3:0},\n";
					++recs;
				}
			}
			ArrayList<String> htmlparts = null;
			if (providername == null) {
				htmlparts = mcache.getHTMLParts((MimeMessage) m, msgnum, false);
			} else {
				htmlparts = mcache.getHTMLParts((MimeMessage) m, providername, providerid);
			}
			for (String html : htmlparts) {
				sout += "{iddata:'html',value1:'" + OldUtils.jsEscape(html) + "',value2:'',value3:0},\n";
				++recs;
			}
			HTMLMailData mailData = mcache.getMailData((MimeMessage) m);
			int acount = mailData.getAttachmentPartCount();
			for (int i = 0; i < acount; ++i) {
				Part p = mailData.getAttachmentPart(i);
				String ctype = p.getContentType();
				System.out.println("attachment " + i + " is " + ctype);
				int ix = ctype.indexOf(';');
				if (ix > 0) {
					ctype = ctype.substring(0, ix);
				}
				boolean isInlineable = isInlineableMime(ctype);
				boolean inline = (p.getHeader("Content-Location") != null) || (p.getHeader("Content-ID") != null) && isInlineable;
				if (p.getDisposition() != null && p.getDisposition().equalsIgnoreCase(Part.INLINE) && inline) {
					continue;
				}
				System.out.println("attachment is not inline");
				
				String imgname = null;
				if (ctype.equalsIgnoreCase("text/calendar")) {
					imgname = "resources/" + getManifest().getId().replaceAll(".", "/") + "/laf/" + environment.getCoreUserSettings().getLookAndFeel() + "/service-small.gif";
				}
				
				String pname = getPartName(p);
				if (pname == null) {
					ix = ctype.indexOf("/");
					String fname = ctype;
					if (ix > 0) {
						fname = ctype.substring(ix + 1);
					}
					String ext = WT.getExtension(ctype);
					if (ext == null) {
						pname = fname;
					} else {
						pname = fname + "." + ext;
					}
				}
				int size = p.getSize();
				int lines = (size / 76);
				int rsize = size - (lines * 2);//(p.getSize()/4)*3;
				String iddata = ctype.equalsIgnoreCase("message/rfc822") ? "eml" : (inline ? "inlineattach" : "attach");
				
				sout += "{iddata:'" + iddata + "',value1:'" + (i + idattach) + "',value2:'" + OldUtils.jsEscape(OldUtils.htmlescape(pname)) + "',value3:" + rsize + ",value4:" + (imgname == null ? "null" : "'" + OldUtils.jsEscape(imgname) + "'") + "},\n";
			}
			if (!mcache.isDrafts() && !mcache.isSent() && !mcache.isSpam() && !mcache.isTrash()) {
				if (vheader != null && vheader[0] != null && !isseen) {
					sout += "{iddata:'receipt',value1:'" + OldUtils.jsEscape(vheader[0]) + "',value2:'',value3:0},\n";
				}
			}
			
			String h = getSingleHeaderValue(m, "Sonicle-send-scheduled");
			if (h != null && h.equals("true")) {
				Calendar scal = parseScheduleHeader(getSingleHeaderValue(m, "Sonicle-send-date"), getSingleHeaderValue(m, "Sonicle-send-time"));
				java.util.Date sd = scal.getTime();
				String sdate = df.format(sd).replaceAll("\\.", ":");
				sout += "{iddata:'scheddate',value1:'" + OldUtils.jsEscape(sdate) + "',value2:'',value3:0},\n";
			}			
			
			sout += "{iddata:'date',value1:'" + OldUtils.jsEscape(date) + "',value2:'',value3:0},\n";
			sout += "{iddata:'subject',value1:'" + OldUtils.jsEscape(OldUtils.htmlescape(subject)) + "',value2:'',value3:0}\n";
			
			if (providername == null && !mcache.isSpecial()) {
				mcache.refreshUnreads();
			}
			long millis = System.currentTimeMillis();
			sout += "\n],\ntotalRecords:" + recs + ",\nmillis:" + millis + "\n}\n";
			out.println(sout);
//            if (!wasopen) folder.close(false);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processGetMessageNote(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		UserProfile profile = environment.getProfile();
		Connection con = null;
		Statement stmt = null;
		ResultSet rs = null;
		boolean result = false;
		String text = "";
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(pfoldername);
			int msgnum = Integer.parseInt(pidmessage);
			String id = getMessageID(mcache.getMessage(msgnum));
			con = getConnection();
			stmt = con.createStatement();
			rs = stmt.executeQuery("select text from mailnotes where iddomain='" + profile.getDomainId() + "' and messageid='" + OldUtils.getSQLString(id) + "'");
			if (rs.next()) {
				text = rs.getString("text");
			}
			result = true;
		} catch (Exception exc) {
			exc.printStackTrace();
			text = exc.getMessage();
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (Exception exc) {
				}
			}
			if (stmt != null) {
				try {
					stmt.close();
				} catch (Exception exc) {
				}
			}
			if (con != null) {
				try {
					con.close();
				} catch (Exception exc) {
				}
			}
		}
		new JsonResult(result, text).printTo(out);
	}
	
	public void processSaveMessageNote(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String text = request.getParameter("text").trim();
		UserProfile profile = environment.getProfile();
		Connection con = null;
		boolean result = false;
		String message = "";
		try {
			checkStoreConnected();
			FolderCache mcache = getFolderCache(pfoldername);
			int msgnum = Integer.parseInt(pidmessage);
			Message msg = mcache.getMessage(msgnum);
			String id = getMessageID(msg);
			con = getConnection();
			NoteDAO.getInstance().deleteById(con, profile.getDomainId(), id);
            //stmt=con.createStatement();
			//stmt.executeUpdate("delete from mailnotes where iddomain='"+profile.getDomainId()+"' and messageid='"+OldUtils.getSQLString(id)+"'");
			if (text.length() > 0) {
				ONote onote = new ONote(profile.getDomainId(), id, text);
				NoteDAO.getInstance().insert(con, onote);
                //pstmt=con.prepareStatement("insert into mailnotes (iddomain,messageid,text) values (?,?,?)");
				//pstmt.setString(1,profile.getDomainId());
				//pstmt.setString(2, id);
				//pstmt.setString(3, text);
				//pstmt.execute();
				msg.setFlags(flagNote, true);
			} else {
				msg.setFlags(flagNote, false);
			}
			result = true;
		} catch (Exception exc) {
			exc.printStackTrace();
			message = exc.getMessage();
		} finally {
			DbUtils.closeQuietly(con);
		}
		new JsonResult(result, message).printTo(out);
	}

	private String adjustEmail(String email) {
		if (email != null) {
			email = email.trim();
			if (email.startsWith("'")) {
				email = email.substring(1);
			}
			if (email.endsWith("'")) {
				email = email.substring(0, email.length() - 1);
			}
			email = email.trim();
		}
		return email;
	}
	
	class ContactElement {

		String email;
		String source;
		
		ContactElement(String email, String source) {
			this.email = email;
			this.source = source;
		}
		
		public boolean equals(Object o) {
			ContactElement contact = (ContactElement) o;
			return email.equals(contact.email);
		}
	}

	// TODO: lookupContacts!!!!!!!!!!
/*    public void processLookupContacts(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String query=request.getParameter("query");
	 String context=request.getParameter("context");
	 String group=request.getParameter("group");
	 if (group!=null && (group.trim().length()==0||group.trim().equals("*"))) group=null;
	 String text="";
	 String text2="";
	 if (query!=null) text=query.trim();
	 String otext=text;
	 if (text.contains(" ")) {
	 String ss[]=text.split(" ");
	 String str="";
	 for(String s: ss) {
	 str+="%"+s.trim()+"% ";
	 }
	 text=str.trim();
	 text2 = text;
	 } else {
	 text+="%";
	 text2+="%"+text;
	 }

	 String sout="{ contacts: [\n";
	 String xsout="";
	 ArrayList<String> patterns=new ArrayList<String>();
	 patterns.add(text);
	 patterns.add(text);
	 patterns.add(text);
	 if (!text.contains("@")) {
	 String s="%@"+otext;
	 //if (!otext.contains(".")) s+=".";
	 s+="%";
	 patterns.add(s);
	 }

	 HttpSession session=request.getSession();
	 //WebTopSession wts=(WebTopSession)session.getAttribute("webtopsession");
	 int recs=0;
	 ArrayList<ContactElement> values=new ArrayList<ContactElement>();
	 String autodesc=lookupResource(wts.getEnvironment().getUserProfile(), MailLocaleKey.RECIPIENTS_SOURCE_AUTO);
	 if (group==null) {
	 boolean doauto=true;
	 String xs=wta.getDomainSetting(wts.getEnvironment().getWebTopDomain(), "contacts.suggestautosaved");
	 if (xs!=null && xs.equals("N")) doauto=false;
            
	 if (doauto) {
	 //Start with auto storage
	 ArrayList<WebTopSession.ServiceStoreEntry> storevalues=wts.getServiceStoreEntries(getName(), context, query.toUpperCase(), 10);
	 for(WebTopSession.ServiceStoreEntry sse: storevalues) {
	 ContactElement ce=new ContactElement(sse.getValue(),null);
	 if (ce.email.length()>0 && ce.email.contains("@") && !values.contains(ce)) values.add(ce);
	 }
	 }
            
	 //Now contacts
	 ContactsService cs=(ContactsService)wts.getServiceByName("contacts");
	 for (DirectoryManager dm: cs.getDirectoryManagers()) {
	 if (dm instanceof LDAPDirectoryManager) continue;
	 ArrayList<String> searchFields=new ArrayList<String>();
	 searchFields.add(dm.getFirstNameField());
	 searchFields.add(dm.getLastNameField());
	 searchFields.add(dm.getMailField());
	 if (patterns.size()==4) searchFields.add(dm.getMailField());
	 if(dm.getCompanyField() != null) {
	 searchFields.add(dm.getCompanyField());
	 patterns.add(text2);
	 }
				
	 ContactElement cex[]=lookup(wts,dm,searchFields,patterns);
	 for (ContactElement ce: cex) {
	 if (ce.email.length()>0) { 
	 int ix=values.indexOf(ce);
	 if (ix<0) values.add(ce);
	 else {
	 ContactElement cep=values.get(ix);
	 if (cep.source==null) cep.source=ce.source;
	 }
	 }
	 }
	 }
            
	 } else {
	 ContactsService cs=(ContactsService)wts.getServiceByName("contacts");
	 DirectoryManager dm=cs.getDirectoryManager(group);
	 ArrayList<String> searchFields=new ArrayList<String>();
	 searchFields.add(dm.getFirstNameField());
	 searchFields.add(dm.getLastNameField());
	 searchFields.add(dm.getMailField());
	 if (patterns.size()==4) searchFields.add(dm.getMailField());
	 if(dm.getCompanyField() != null) {
	 searchFields.add(dm.getCompanyField());
	 patterns.add(text2);
	 }
			
	 ContactElement cex[]=lookup(wts,dm,searchFields,patterns);
	 for (ContactElement ce: cex) {
	 if (ce.email.length()>0) { 
	 int ix=values.indexOf(ce);
	 if (ix<0) values.add(ce);
	 else {
	 ContactElement cep=values.get(ix);
	 if (cep.source==null) cep.source=ce.source;
	 }
	 }
	 }
	 }

	 boolean first=true;
	 for(ContactElement ce: values) {
	 if (!first) xsout+=",\n";
	 xsout+=" { email: \""+OldUtils.jsEscape(OldUtils.htmlescape(ce.email))+"\", source: '["+OldUtils.jsEscape(OldUtils.htmlescape(ce.source==null?autodesc:ce.source))+"]'}";
	 ++recs;
	 first=false;
	 }
	 sout+=xsout+"\n],\ntotalRecords:"+recs+"\n}\n";
	 out.println(sout);
	 }*/
	// TODO: lookupFaxContacts
/*    public void processLookupFaxContacts(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String query=request.getParameter("query");
	 String context=request.getParameter("context");
	 String group=request.getParameter("group");
	 if (group!=null && (group.trim().length()==0||group.trim().equals("*"))) group=null;
	 String text="";
	 if (query!=null) text=query.trim();
	 String otext=text;
	 if (text.contains(" ")) {
	 String ss[]=text.split(" ");
	 String str="";
	 for(String s: ss) {
	 str+="%"+s.trim()+"% ";
	 }
	 text=str.trim();
	 }
	 else text+="%";

	 String sout="{ contacts: [\n";
	 String xsout="";
	 ArrayList<String> patterns=new ArrayList<String>();
	 patterns.add(text);
	 patterns.add(text);
	 patterns.add(text);
	 if (!text.contains("@")) {
	 String s="%@"+otext;
	 //if (!otext.contains(".")) s+=".";
	 s+="%";
	 patterns.add(s);
	 }

	 boolean first=true;
	 HttpSession session=request.getSession();
	 WebTopSession wts=(WebTopSession)session.getAttribute("webtopsession");
	 int recs=0;
	 if (group==null) {

	 //now contacts
	 ContactsService cs=(ContactsService)wts.getServiceByName("contacts");
	 for (DirectoryManager dm: cs.getDirectoryManagers()) {
	 if (dm instanceof LDAPDirectoryManager) continue;
	 ArrayList<String> searchFields=new ArrayList<String>();
	 searchFields.add(dm.getFirstNameField());
	 searchFields.add(dm.getLastNameField());
	 searchFields.add(dm.getMailField());
	 if (patterns.size()==4) searchFields.add(dm.getMailField());
	 String s=lookupFax(wts,dm,searchFields,patterns);
	 if (s.length()>0) {
	 if (!first) xsout+=",\n";
	 xsout+=s;
	 ++recs;
	 first=false;
	 }
	 }
	 } else {
	 ContactsService cs=(ContactsService)wts.getServiceByName("contacts");
	 DirectoryManager dm=cs.getDirectoryManager(group);
	 ArrayList<String> searchFields=new ArrayList<String>();
	 searchFields.add(dm.getFirstNameField());
	 searchFields.add(dm.getLastNameField());
	 searchFields.add(dm.getMailField());
	 if (patterns.size()==4) searchFields.add(dm.getMailField());
	 String s=lookupFax(wts,dm,searchFields,patterns);
	 if (s.length()>0) {
	 if (!first) xsout+=",\n";
	 xsout+=s;
	 ++recs;
	 first=false;
	 }
	 }

	 sout+=xsout+"\n],\ntotalRecords:"+recs+"\n}\n";
	 out.println(sout);
	 }*/
	// TODO: lookup
/*    private ContactElement[] lookup(WebTopSession wts, DirectoryManager dm, List<String> searchFields, List<String> patterns) {
	 String emailfield=dm.getMailField();
	 String fnfield=dm.getFirstNameField();
	 String lnfield=dm.getLastNameField();
	 Locale locale=wts.getEnvironment().getUserProfile().getLocale();
	 DirectoryResult dr=dm.lookup(searchFields,patterns,locale,false,false,false);
	 int n=dr.getElementsCount();
	 ArrayList<String> emails=new ArrayList<String>();
	 for(int i=0;i<n;++i) {
	 DirectoryElement de=dr.elementAt(i);
	 String email=de.getField(emailfield).trim();
	 if (email.length()==0) continue;
	 String fname=null;
	 String lname=null;
	 if (fnfield!=null) fname=de.getField(fnfield);
	 if (lnfield!=null) lname=de.getField(lnfield);
	 if (fname==null) fname="";
	 if (lname==null) lname="";
	 fname=fname.trim();
	 lname=lname.trim();
	 String cemail=fname;
	 if (cemail.length()>0) cemail+=" ";
	 cemail+=lname;
	 if (cemail.length()>0) cemail+=" <"+email+">";
	 else cemail=email;
	 if (!emails.contains(cemail)) emails.add(cemail);
	 }
	 ContactElement ce[]=new ContactElement[emails.size()];
	 for(int i=0;i<ce.length;++i) {
	 ce[i]=new ContactElement(emails.get(i),dm.getDescription());
	 }
	 return ce;
	 }

	 private String lookupFax(WebTopSession wts, DirectoryManager dm, List<String> searchFields, List<String> patterns) {
	 String sout="";
	 String faxfield=dm.getFaxField();
	 String fnfield=dm.getFirstNameField();
	 String lnfield=dm.getLastNameField();

	 Locale locale=wts.getEnvironment().getUserProfile().getLocale();
	 DirectoryResult dr=dm.lookup(searchFields,patterns,locale,false,false,false);
	 int n=dr.getElementsCount();
	 ArrayList<String> emails=new ArrayList<String>();
	 for(int i=0;i<n;++i) {
	 DirectoryElement de=dr.elementAt(i);
	 String fax=de.getField(faxfield);
	 if (fax==null) continue;
	 fax=fax.trim();
	 if (fax.length()==0) continue;
	 String fname=null;
	 String lname=null;
	 if (fnfield!=null) fname=de.getField(fnfield);
	 if (lnfield!=null) lname=de.getField(lnfield);
	 if (fname==null) fname="";
	 if (lname==null) lname="";
	 fname=fname.trim();
	 lname=lname.trim();
	 String username=fname.toLowerCase().replaceAll(" ", ".")+"."+lname.toLowerCase().replaceAll(" ", ".");
	 String cemail=fname;
	 if (cemail.length()>0) cemail+=" ";
	 cemail+=lname;
	 fax=fixFax(fax);
	 String email=wts.getEnvironment().getWebTopDomain().getSetting("fax.pattern");
	 email=email.replace("{username}", username).replace("{number}", fax);
	 if (cemail.length()>0) cemail+=" <"+email+">";
	 else cemail=email;
	 if (!emails.contains(cemail)) emails.add(cemail);
	 //            sout+=" { email: \""+OldUtils.jsEscape(OldUtils.htmlescape(cemail))+"\" }";
	 }
	 for(String cemail: emails) {
	 if (sout.length()>0) sout+=",\n";
	 sout+=" { email: \""+OldUtils.jsEscape(OldUtils.htmlescape(cemail))+"\" }";
	 }
	 return sout;
	 }

	 private String fixFax(String fax) {
	 StringBuffer newfax=new StringBuffer();
	 for(char c: fax.toCharArray()) {
	 if (Character.isDigit(c)) newfax.append(c);
	 else if (c=='+') {
	 if (newfax.length()==0) newfax.append("00");
	 }
	 }
	 return newfax.toString();
	 }*/
	public void processGetAttachment(HttpServletRequest request, HttpServletResponse response) {
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String pidattach = request.getParameter("idattach");
		String providername = request.getParameter("provider");
		String providerid = request.getParameter("providerid");
		String pcid = request.getParameter("cid");
		String purl = request.getParameter("url");
		String punknown = request.getParameter("unknown");
		String psaveas = request.getParameter("saveas");
		
		try {
			checkStoreConnected();
			FolderCache mcache = null;
			Message m = null;
			if (providername == null) {
				mcache = getFolderCache(pfoldername);
				int newmsgid = Integer.parseInt(pidmessage);
				m = mcache.getMessage(newmsgid);
			} else {
				mcache = fcProvided;
				m = mcache.getProvidedMessage(providername, providerid);
			}
			HTMLMailData mailData = mcache.getMailData((MimeMessage) m);
			Part part = null;
			if (pcid != null) {
				part = mailData.getCidPart(pcid);
			} else if (purl != null) {
				part = mailData.getUrlPart(purl);
			} else if (pidattach != null) {
				part = mailData.getAttachmentPart(Integer.parseInt(pidattach));
			} else if (punknown != null) {
				part = mailData.getUnknownPart(Integer.parseInt(punknown));
			}

			//String ctype="application/octet-stream";
			String ctype = "binary/octet-stream";
			if (psaveas == null) {
				ctype = part.getContentType();
				int ix = ctype.indexOf(";");
				if (ix > 0) {
					ctype = ctype.substring(0, ix);
				}
			}
			String name = part.getFileName();
			if (name == null) {
				name = "";
			}
			try {
				name = OldUtils.decodeQString(name);
			} catch (Exception exc) {
			}
			
			if (psaveas == null) {
				int ix = name.lastIndexOf(".");
				if (ix > 0) {
					String ext = name.substring(ix + 1);
					String xctype = WT.getContentType(ext);
					if (xctype != null) {
						ctype = xctype;
					}
				}
			}
			response.setContentType(ctype);
			response.setHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
			if (providername == null) {
				Folder folder = mailData.getFolder();
				if (!folder.isOpen()) {
					folder.open(Folder.READ_ONLY);
				}
			}
			InputStream is = part.getInputStream();
			OutputStream out = response.getOutputStream();
			fastStreamCopy(is, out);
			/*            byte[] b = new byte[64 * 1024];
			 int len = 0;

			 try {
			 while ((len = is.read(b)) != -1)
			 out.write(b, 0, len);
			 } catch(Exception exc) {
			 exc.printStackTrace();
			 }
			 out.flush();
			 out.close();
			 is.close();*/
			
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processGetAttachments(HttpServletRequest request, HttpServletResponse response) {
		String pfoldername = request.getParameter("folder");
		String pidmessage = request.getParameter("idmessage");
		String pids[] = request.getParameterValues("ids");
		String providername = request.getParameter("provider");
		String providerid = request.getParameter("providerid");
		
		try {
			checkStoreConnected();
			FolderCache mcache = null;
			Message m = null;
			if (providername == null) {
				mcache = getFolderCache(pfoldername);
				int newmsgid = Integer.parseInt(pidmessage);
				m = mcache.getMessage(newmsgid);
			} else {
				mcache = fcProvided;
				m = mcache.getProvidedMessage(providername, providerid);
			}
			HTMLMailData mailData = mcache.getMailData((MimeMessage) m);
			String name = m.getSubject();
			if (name == null) {
				name = "attachments";
			}
			try {
				name = OldUtils.decodeQString(name);
			} catch (Exception exc) {
			}
			name += ".zip";
			response.setContentType("application/x-zip-compressed");
			response.setHeader("Content-Disposition", "inline; filename=\"" + name + "\"");
			JarOutputStream jos = new java.util.jar.JarOutputStream(response.getOutputStream());
			byte[] b = new byte[64 * 1024];
			for (String pid : pids) {
				Part part = mailData.getAttachmentPart(Integer.parseInt(pid));
				String pname = part.getFileName();
				if (pname == null) {
					pname = "unknown";
				}
				try {
					pname = OldUtils.decodeQString(pname, "iso-8859-1");
				} catch (Exception exc) {
				}
				JarEntry je = new JarEntry(pname);
				jos.putNextEntry(je);
				if (providername == null) {
					Folder folder = mailData.getFolder();
					if (!folder.isOpen()) {
						folder.open(Folder.READ_ONLY);
					}
				}
				InputStream is = part.getInputStream();
				int len = 0;
				while ((len = is.read(b)) != -1) {
					jos.write(b, 0, len);
				}
				is.close();
			}
			jos.closeEntry();
			jos.flush();
			jos.close();
			
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processPreviewAttachment(HttpServletRequest request, HttpServletResponse response) {
		try {
			checkStoreConnected();
			int msgid = Integer.parseInt(request.getParameter("newmsgid"));
			String tempname = request.getParameter("tempname");
			String cid = request.getParameter("cid");
			Attachment att = null;
			if (tempname != null) {
				att = getAttachment(msgid, tempname);
			} else if (cid != null) {
				att = getAttachmentByCid(msgid, cid);
			}
			if (att != null) {
				String ctype = att.getContentType();
				response.setContentType(ctype);
				response.setHeader("Content-Disposition", "filename=\"" + att.getName() + "\"");
				
				InputStream is = new FileInputStream(att.getFile());
				OutputStream oout = response.getOutputStream();
				fastStreamCopy(is, oout);
				/*                    byte[] b = new byte[4 * 1024];
				 int len = 0;

				 while ((len = is.read(b)) != -1)
				 oout.write(b, 0, len);
				 oout.flush();
				 oout.close();
				 return;*/
			} else {
				System.out.println("att was null!!!");
			}
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public Calendar convertTimeZone(String year, String month, String day, String hour, String min, String timezonefrom, String timezoneto) {
		TimeZone timeZone1 = TimeZone.getTimeZone(timezonefrom);
		TimeZone timeZone2 = TimeZone.getTimeZone(timezoneto);
		Calendar calendar = new GregorianCalendar();
		calendar.setTimeZone(timeZone1);
		calendar.set(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day), Integer.parseInt(hour), Integer.parseInt(min));
		Calendar calendarout = new GregorianCalendar();
		calendarout.setTimeZone(timeZone2);
		calendarout.setTimeInMillis(calendar.getTimeInMillis());
		return calendarout;
	}

    // TODO: get calendar event!!!
/*    public void processGetCalendarEvent(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String pfoldername=request.getParameter("folder");
	 String pidmessage=request.getParameter("idmessage");
	 String pidattach=request.getParameter("idattach");
	 UserProfile profile=environment.getProfile();
	 JsonObject sout=new JsonObject();
	 JsonArray partecipant=new JsonArray();
	 //String sout;
	 Connection con = null;
	 Statement stmt = null;
	 ResultSet rset = null;
	 String event_id="";
	 try {
	 checkStoreConnected();
	 FolderCache mcache=getFolderCache(pfoldername);
	 int newmsgid=Integer.parseInt(pidmessage);
	 Message m=mcache.getMessage(newmsgid);
	 HTMLMailData mailData=mcache.getMailData((MimeMessage)m);
	 Part part=mailData.getAttachmentPart(Integer.parseInt(pidattach));
            
	 System.setProperty("ical4j.unfolding.relaxed", "true");
	 System.setProperty("ical4j.parsing.relaxed", "true");
	 net.fortuna.ical4j.data.CalendarBuilder cb=new net.fortuna.ical4j.data.CalendarBuilder();
	 net.fortuna.ical4j.model.Calendar cal=cb.build(part.getInputStream());
	 net.fortuna.ical4j.model.component.VEvent vevent=null;
	 for (Iterator xi = cal.getComponents().iterator(); xi.hasNext();) {
                
	 net.fortuna.ical4j.model.Component component = (net.fortuna.ical4j.model.Component) xi.next();
	 if (component instanceof net.fortuna.ical4j.model.component.VEvent) {
	 vevent=(net.fortuna.ical4j.model.component.VEvent)component;
	 break;
	 }
	 }
            
	 if (vevent==null){
	 //sout="{\nresult: false, text:'Event not found!'\n}";
	 sout.addProperty("result", false);
	 sout.addProperty("text", "Event not found!");
                
	 }
	 else {
	 String event=vevent.getSummary().getValue();
	 String description=vevent.getDescription()==null?"":vevent.getDescription().getValue();
	 String location=vevent.getLocation()==null?"":vevent.getLocation().getValue();
	 String uid=vevent.getUid().getValue();
	 Method method=cal.getMethod();
	 con = getConnection();
	 stmt = con.createStatement();
	 System.out.println(
	 "SELECT EVENT_ID "+
	 "FROM EVENTS "+
	 "WHERE UID_PLANNING='"+uid+"' AND STATUS!='D' AND IDDOMAIN='"+profile.getDomainId()+"' AND EVENT_BY='"+profile.getUserId()+"'"
	 );
	 rset = stmt.executeQuery(
	 "SELECT EVENT_ID "+
	 "FROM EVENTS "+
	 "WHERE UID_PLANNING='"+uid+"' AND STATUS!='D' AND IDDOMAIN='"+profile.getDomainId()+"' AND EVENT_BY='"+profile.getUserId()+"'"
	 );
	 if (rset.next()) {
	 event_id = rset.getString("event_id");
	 }
                
                
	 java.util.Date starts=vevent.getStartDate().getDate();
                

	 java.util.Date ends=vevent.getEndDate().getDate();
	 if (ends==null || ends.before(starts)) ends=starts;
	 Calendar xcal=Calendar.getInstance();
	 xcal.setTime(starts);
                
	 //ricorrenza
	 boolean recurrence=false;
	 String recurr_type="";
	 String until_date_dd="";
	 String until_date_mm="";
	 String until_date_yyyy="";
	 String dayly_freq="";
	 String weekly_freq="";
	 String weekly_day1="false";
	 String weekly_day2="false"; 
	 String weekly_day3="false";
	 String weekly_day4="false";
	 String weekly_day5="false";
	 String weekly_day6="false";
	 String weekly_day7="false";
	 String monthly_month="";
	 String monthly_day="";
	 String yearly_day="";
	 String yearly_month="";
	 String repeat="0";
	 String permanent="";
	 RRule rrule = (RRule) vevent.getProperties().getProperty(Property.RRULE);
	 if (rrule!=null){
	 recurrence=true;
	 recurr_type=rrule.getRecur().getFrequency();    //frequenza
                    
	 java.util.Date until =rrule.getRecur().getUntil();  //data di fine
	 if (until!=null){
	 Calendar u=Calendar.getInstance();
	 u.setTime(until);
	 until_date_dd=u.get(Calendar.DAY_OF_MONTH)+"";
	 until_date_mm=u.get(Calendar.MONTH)+1+"";
	 until_date_yyyy=u.get(Calendar.YEAR)+"";
	 }else{
	 permanent="true";
	 }   
	 if (recurr_type.equals("DAILY")){
	 WeekDayList dayList = rrule.getRecur().getDayList();
	 System.out.println("daylylist="+dayList);
	 if (!dayList.isEmpty()){ //ricorrenza giornaliera feriale
	 recurr_type="F";
	 }else{
	 recurr_type="D"; //ricorrenza giornaliera con intervallo   
	 dayly_freq=String.valueOf(rrule.getRecur().getInterval());
	 if (dayly_freq.equals("-1")) dayly_freq="1";
	 }
	 }else if (recurr_type.equals("WEEKLY")){
	 recurr_type="W";
	 weekly_freq=String.valueOf(rrule.getRecur().getInterval());
	 if (weekly_freq.equals("-1")) weekly_freq="1";
	 WeekDayList dayList = rrule.getRecur().getDayList();
	 if (!dayList.isEmpty()){
	 for (Object o:dayList){
	 WeekDay weekday=(WeekDay)o;
	 if (weekday.getDay().equals("MO")) weekly_day1="true";
	 if (weekday.getDay().equals("TU")) weekly_day2="true"; 
	 if (weekday.getDay().equals("WE")) weekly_day3="true";
	 if (weekday.getDay().equals("TH")) weekly_day4="true";
	 if (weekday.getDay().equals("FR")) weekly_day5="true";
	 if (weekday.getDay().equals("SA")) weekly_day6="true";
	 if (weekday.getDay().equals("SU")) weekly_day7="true";
	 }
	 }
                        
	 }else if (recurr_type.equals("MONTHLY")){
	 recurr_type="M";
	 monthly_month=String.valueOf(rrule.getRecur().getInterval());
	 NumberList monthDayList = rrule.getRecur().getMonthDayList();
	 for (Object o:monthDayList){
	 monthly_day=String.valueOf((Integer)o);
	 }
	 }else if (recurr_type.equals("YEARLY")){
	 recurr_type="Y";
	 NumberList monthList = rrule.getRecur().getMonthList();
	 for (Object o:monthList){
	 yearly_month=String.valueOf((Integer)o);
	 }
	 NumberList monthDayList = rrule.getRecur().getMonthDayList();
	 for (Object o:monthDayList){
	 yearly_day=String.valueOf((Integer)o);
	 }
	 }
                    
	 if (rrule.getRecur().getCount()!=-1)
	 repeat=String.valueOf(rrule.getRecur().getCount());
                   
                   
	 }
	 //fine ricorrenza
                
	 PropertyList pl=vevent.getProperties("ATTENDEE");
	 boolean attendees=false;
	 description=description.trim();
	 if (pl!=null) {
	 for(Object op: pl) {
	 Property p=(Property)op;
	 Parameter pcn=p.getParameter("CN");
	 if (pcn!=null) {
	 String cn=pcn.getValue();
	 String vcn[]=cn.split(":");
	 if (vcn.length>0) {
	 if (!attendees) {
	 if (description.length()>0) {
	 if (!description.endsWith("\n")) description+="\n";
	 description+="\n";
	 }
	 //description+=lookupResource(profile, MailLocaleKey.ICAL_ATTENDEES)+":\n";
	 }
                                
	 //description+="- "+vcn[0]+"\n";
	 attendees=true;
                                
	 JsonObject mail=new JsonObject();
	 mail.addProperty("invitation","false");
	 mail.addProperty("name",vcn[0]);
	 mail.addProperty("partecipant","N");
	 mail.addProperty("response","none");
                                
                                
	 partecipant.add(mail);
	 }
	 }
	 }
	 }
	 if (description.length()>1024) description=description.substring(0,1024);
                
	 String sgeo="";
	 Geo geo=vevent.getGeographicPos();
	 if (geo!=null) sgeo=geo.getValue();
	 String defaulttimezone = wts.getServiceSetting("calendar", "defaulttimezone", null);
	 if (defaulttimezone==null){
	 defaulttimezone="Europe/Rome";
	 }
	 int syyyy=xcal.get(Calendar.YEAR);
	 int smm=xcal.get(Calendar.MONTH);
	 int sdd=xcal.get(Calendar.DAY_OF_MONTH);
	 int shhh=xcal.get(Calendar.HOUR_OF_DAY);
	 int smmm=xcal.get(Calendar.MINUTE);
	 int ssss=xcal.get(Calendar.SECOND);
	 xcal.setTime(ends);
	 int eyyyy=xcal.get(Calendar.YEAR);
	 int emm=xcal.get(Calendar.MONTH);
	 int edd=xcal.get(Calendar.DAY_OF_MONTH);
	 int ehhh=xcal.get(Calendar.HOUR_OF_DAY);
	 int emmm=xcal.get(Calendar.MINUTE);
	 int esss=xcal.get(Calendar.SECOND);
                
                
	 String organizer=vevent.getOrganizer()==null?"":vevent.getOrganizer().getParameter("CN").getValue();
	 JsonObject eventsout=new JsonObject();
	 sout.addProperty("result", OldUtils.jsEscape(event));
	 eventsout.addProperty("description", description);
	 eventsout.addProperty("summary", OldUtils.jsEscape(event));
	 eventsout.addProperty("location", OldUtils.jsEscape(location));
	 eventsout.addProperty("startyyyy",syyyy);
	 eventsout.addProperty("startmm",smm);
	 eventsout.addProperty("startdd",sdd);
	 eventsout.addProperty("starthh",shhh);
	 eventsout.addProperty("startmmm",smmm);
	 eventsout.addProperty("startssss",ssss);
	 eventsout.addProperty("endyyyy",eyyyy);
	 eventsout.addProperty("endmm",emm);
	 eventsout.addProperty("enddd",edd);
	 eventsout.addProperty("endhh",ehhh);
	 eventsout.addProperty("endmmm",emmm);
	 eventsout.addProperty("endssss",esss);
	 eventsout.addProperty("organizer", OldUtils.jsEscape(organizer));
	 eventsout.addProperty("geo", sgeo);
	 eventsout.addProperty("method",method.getValue());
	 eventsout.addProperty("uid", uid);
	 eventsout.addProperty("event_id",event_id);
	 eventsout.addProperty("recurrence",recurrence);
	 eventsout.addProperty("recurr_type",recurr_type);
	 eventsout.addProperty("until_date_dd",until_date_dd);
	 eventsout.addProperty("until_date_mm",until_date_mm);
	 eventsout.addProperty("until_date_yyyy",until_date_yyyy);
	 eventsout.addProperty("dayly_freq",dayly_freq);
	 eventsout.addProperty("weekly_freq",weekly_freq);
	 eventsout.addProperty("weekly_day1",weekly_day1);
	 eventsout.addProperty("weekly_day2",weekly_day2); 
	 eventsout.addProperty("weekly_day3",weekly_day3);
	 eventsout.addProperty("weekly_day4",weekly_day4);
	 eventsout.addProperty("weekly_day5",weekly_day5);
	 eventsout.addProperty("weekly_day6",weekly_day6);
	 eventsout.addProperty("weekly_day7",weekly_day7);
	 eventsout.addProperty("monthly_month",monthly_month);
	 eventsout.addProperty("monthly_day",monthly_day);
	 eventsout.addProperty("yearly_month",yearly_month);
	 eventsout.addProperty("yearly_day",yearly_day);
	 eventsout.addProperty("repeat",repeat);
	 eventsout.addProperty("permanent",permanent);
	 sout.add("event", eventsout);
	 JsonObject planninggrid=new JsonObject();
	 planninggrid.add("planning",partecipant);
	 sout.add("planninggrid",planninggrid);
	 }
	 } catch(Exception exc) {
	 sout.addProperty("nresult", false);
	 sout.addProperty("text", OldUtils.jsEscape(exc.getMessage()));
	 exc.printStackTrace(System.out);
	 }
	 finally {
	 if (rset!=null) try { rset.close(); } catch(Exception exc) {}
	 if (stmt!=null) try { stmt.close(); } catch(Exception exc) {}
	 if (con!=null) try { con.close(); } catch(Exception exc) {}
	 }
        
        
	 out.println(sout);
	 }*/
	// TODO: list filters!!!
/*    public void processListFilters(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getProfile();
	 Locale locale=profile.getLocale();
	 //WebTopApp webtopapp=environment.getWebTopApp();
	 Connection con=null;
	 String context=request.getParameter("context");
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
	 String table = "none";
	 if (context.equals("INBOX")) table = es.getTempTable("mailfilters").tableName;
	 else if (context.equals("SENT")) table = es.getTempTable("mailsentfilters").tableName;
			
	 con=wtd.getConnection();
	 com.sonicle.webtop.sieve.JSonGenerator jsg=new com.sonicle.webtop.sieve.JSonGenerator(context,this,locale,folderPrefix);
	 MailFilters filters=getMailFilters(con, table, profile.getUser(), profile.getIDDomain());
	 StringBuffer sb=jsg.generate(filters);
	 String sout=sb.toString();
	 out.println(sout);
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 } finally {
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 }*/
	// TODO: move filters!!!
/*    public void processMoveFilters(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getProfile();
	 //WebTopApp webtopapp=environment.getWebTopApp();
	 String context=request.getParameter("context");
	 //String table="none";
	 //if (context.equals("INBOX")) table="mailfilters";
	 //else if (context.equals("SENT")) table="mailsentfilters";
	 String login=profile.getUserId();
	 String pids[]=request.getParameterValues("ids");
	 String ptoid=request.getParameter("toid");
	 int toid=Integer.parseInt(ptoid);
	 int ids[]=new int[pids.length];
	 for(int i=0;i<pids.length;++i)  ids[i]=Integer.parseInt(pids[i]);
	 Arrays.sort(ids);
	 Connection con=null;
	 Statement stmt=null;
	 Statement ustmt=null;
	 ResultSet rs=null;
	 String sout;
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
	 String table = "none";
	 if (context.equals("INBOX")) table = es.getTempTable("mailfilters").tableName;
	 else if (context.equals("SENT")) table = es.getTempTable("mailsentfilters").tableName;
			
	 con=wtd.getConnection();
	 stmt=con.createStatement();
	 ustmt=con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
	 String sql;
	 //temporarily updates ids to -n,...,-1 where -n is first in ids
	 for(int i=0;i<ids.length;++i) {
	 sql="update "+table+" set idfilter="+(-ids.length+i)+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter="+ids[i];
	 stmt.executeUpdate(sql);
	 }
	 //nstart is toid or next one depending on direction
	 boolean backward=toid<ids[0];
	 int nstart=(backward?toid:toid+1);
	 //shift last ids to an impossible range, reversed (-10000 and over)
	 sql="update "+table+" set idfilter=(-10000-idfilter) where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter>="+nstart;
	 stmt.executeUpdate(sql);

	 //renumber from first in ids to nstart-1 (in case selection is moved forward)
	 if (!backward) {
	 int n=ids[0];
	 for(int i=ids[0];i<nstart;++i) {
	 sql="update "+table+" set idfilter="+n+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter="+i;
	 n+=stmt.executeUpdate(sql);
	 }
	 nstart=n;
	 }
	 //move temporary records to its new place
	 for(int i=0;i<ids.length;++i) {
	 sql="update "+table+" set idfilter="+(nstart+i)+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter="+(-ids.length+i);
	 stmt.executeUpdate(sql);
	 }
	 //renumber last ids, select reversed to get original order
	 rs=ustmt.executeQuery("select iddomain,idfilter,login from "+table+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter<=-10000 order by idfilter desc");
	 int n=nstart+ids.length;
	 while(rs.next()) {
	 rs.updateInt("idfilter",n++);
	 rs.updateRow();
	 }
	 rs.close();
	 ustmt.close();
	 stmt.close();
	 con.commit();
	 //suggest new selection indexes, 0 based
	 int n1=nstart-1;
	 int n2=(nstart+ids.length-1)-1;
	 sout="{\nresult: true, n1:"+n1+", n2:"+n2+"\n}";
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } finally {
	 if (rs!=null) try { rs.close(); } catch(Exception e) {}
	 if (ustmt!=null) try { ustmt.close(); } catch(Exception e) {}
	 if (stmt!=null) try { stmt.close(); } catch(Exception e) {}
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 out.println(sout);
	 }*/
	// TODO: delete filters!!!
/*    public void processDeleteFilters(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getProfile();
	 //WebTopApp webtopapp=environment.getWebTopApp();
	 String context=request.getParameter("context");
	 //String table="none";
	 //if (context.equals("INBOX")) table="mailfilters";
	 //else if (context.equals("SENT")) table="mailsentfilters";
	 String login=profile.getUserId();
	 String pids[]=request.getParameterValues("ids");
	 int ids[]=new int[pids.length];
	 for(int i=0;i<pids.length;++i)  ids[i]=Integer.parseInt(pids[i]);
	 Arrays.sort(ids);
	 Connection con=null;
	 Statement stmt=null;
	 Statement ustmt=null;
	 ResultSet rs=null;
	 String sout;
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
	 String table = "none";
	 if (context.equals("INBOX")) table = es.getTempTable("mailfilters").tableName;
	 else if (context.equals("SENT")) table = es.getTempTable("mailsentfilters").tableName;
			
	 con=wtd.getConnection();
	 stmt=con.createStatement();
	 ustmt=con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_UPDATABLE);
	 String sql="delete from "+table+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter in (";
	 for(int i=0;i<ids.length;++i) {
	 if (i>0) sql+=",";
	 sql+=ids[i];
	 }
	 sql+=")";
	 stmt.executeUpdate(sql);
	 //renumber from first deleted to last available
	 int n=ids[0];
	 rs=ustmt.executeQuery("select iddomain,idfilter,login from "+table+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter>"+n);
	 while(rs.next()) {
	 rs.updateInt("idfilter", n++);
	 rs.updateRow();
	 }
	 rs.close();
	 ustmt.close();
	 stmt.close();
	 con.commit();
	 sout="{\nresult: true\n}";
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } finally {
	 if (rs!=null) try { rs.close(); } catch(Exception e) {}
	 if (ustmt!=null) try { ustmt.close(); } catch(Exception e) {}
	 if (stmt!=null) try { stmt.close(); } catch(Exception e) {}
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 out.println(sout);
	 }*/
	// TODO: set filters status
/*    public void processSetFiltersStatus(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getProfile();
	 //WebTopApp webtopapp=environment.getWebTopApp();
	 String context=request.getParameter("context");
	 //String table="none";
	 //if (context.equals("INBOX")) table="mailfilters";
	 //else if (context.equals("SENT")) table="mailsentfilters";
	 String login=profile.getUserId();
	 String pids[]=request.getParameterValues("ids");
	 String status=request.getParameter("status");
	 Connection con=null;
	 Statement stmt=null;
	 ResultSet rs=null;
	 String sout;
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
	 String table = "none";
	 if (context.equals("INBOX")) table = es.getTempTable("mailfilters").tableName;
	 else if (context.equals("SENT")) table = es.getTempTable("mailsentfilters").tableName;
			
	 con=wtd.getConnection();
	 stmt=con.createStatement();
	 String sql="update "+table+" set status='"+status+"' where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter in (";
	 for(int i=0;i<pids.length;++i) {
	 if (i>0) sql+=",";
	 sql+=pids[i];
	 }
	 sql+=")";
	 stmt.executeUpdate(sql);
	 stmt.close();
	 con.commit();
	 sout="{\nresult: true\n}";
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } finally {
	 if (stmt!=null) try { stmt.close(); } catch(Exception e) {}
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 out.println(sout);
	 }*/
	// TODO: save filter
/*    public void processSaveFilter(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getProfile();
	 //WebTopApp webtopapp=environment.getWebTopApp();
	 String context=request.getParameter("context");
	 //String table="none";
	 //if (context.equals("INBOX")) table="mailfilters";
	 //else if (context.equals("SENT")) table="mailsentfilters";
	 String login=profile.getUserId();
	 String sidfilter=request.getParameter("idfilter");
	 int idfilter=0;
	 if (sidfilter!=null && sidfilter.trim().length()>0) idfilter=Integer.parseInt(sidfilter);
	 int newidfilter=0;
	 String action=request.getParameter("filteraction").toUpperCase();
	 String actionvalue=request.getParameter("actionvalue");

	 Connection con=null;
	 Statement stmt=null;
	 ResultSet rs=null;
	 String sout;
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
	 String table = "none";
	 if (context.equals("INBOX")) table = es.getTempTable("mailfilters").tableName;
	 else if (context.equals("SENT")) table = es.getTempTable("mailsentfilters").tableName;
			
	 con=wtd.getConnection();
	 stmt=con.createStatement();

	 String sql;
	 if (idfilter>0) {
	 sql="delete from "+table+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+profile.getUser()+"' and idfilter="+idfilter;
	 stmt.executeUpdate(sql);
	 newidfilter=idfilter;
	 } else {
	 sql="select max(idfilter) as newidfilter from "+table+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+profile.getUser()+"'";
	 rs=stmt.executeQuery(sql);
	 rs.next();
	 newidfilter=rs.getInt("newidfilter")+1;
	 rs.close();
	 }

	 sql="insert into "+table+" ("+
	 "iddomain,login,idfilter,status,continue,keepcopy,condition,"+
	 "fromvalue,tovalue,subjectvalue,"+
	 "action,actionvalue"+
	 ") values ("+
	 "'"+wtd.getDataIDDomain()+"',"+
	 "'"+profile.getUser()+"',"+
	 newidfilter+","+
	 "'E','N','N',"+
	 "'"+request.getParameter("condition")+"',"+
	 "'"+OldUtils.getSQLString(request.getParameter("fromvalue"))+"',"+
	 "'"+OldUtils.getSQLString(request.getParameter("tovalue"))+"',"+
	 "'"+OldUtils.getSQLString(request.getParameter("subjectvalue"))+"',"+
	 "'"+action+"',"+
	 "'"+OldUtils.getSQLString(actionvalue)+"'"+
	 ")";
	 stmt.executeUpdate(sql);

	 stmt.close();
	 con.commit();
	 sout="{\nresult: true\n}";
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } finally {
	 if (rs!=null) try { rs.close(); } catch(Exception e) {}
	 if (stmt!=null) try { stmt.close(); } catch(Exception e) {}
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 out.println(sout);
	 }*/
	// TODO: apply filters!
/*    public void processApplyFilters(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getUserProfile();
	 String login=profile.getUser();
	 String iddomain=wtd.getDataIDDomain();
	 String vactive=request.getParameter("vactive");
	 String vmessage=request.getParameter("vmessage");
	 String vaddresses=request.getParameter("vaddresses");
	 Connection con=null;
	 Statement stmt=null;
		
	 String sout;
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
			
	 con=wtd.getConnection();
			
	 try {
	 con.setAutoCommit(false);
	 InboxMailFiltersDb.deleteByDomainUser(con, profile.getIDDomain(), profile.getUser());
	 SentMailFiltersDb.deleteByDomainUser(con, profile.getIDDomain(), profile.getUser());
	 es.copyBackTempTable(con, "mailfilters");
	 es.copyBackTempTable(con, "mailsentfilters");
	 con.commit();
	 } catch(SQLException ex1) {
	 con.rollback();
	 throw ex1;
	 }
			
	 con.setAutoCommit(true);
	 stmt=con.createStatement();
	 stmt.executeUpdate("delete from vacation where iddomain='"+iddomain+"' and login='"+profile.getUser()+"'");

	 String sql="insert into vacation (iddomain,login,active,message,addresses) values ("+
	 "'"+wtd.getLocalIDDomain()+"',"+
	 "'"+login+"',"+
	 "'"+vactive+"',"+
	 "'"+OldUtils.getSQLString(vmessage)+"',"+
	 "'"+OldUtils.getSQLString(vaddresses)+"'"+
	 ")";
	 stmt.executeUpdate(sql);
	 con.commit();
	 MailFilters filters=getMailFilters(con,"mailfilters",login,iddomain);
	 if (filters!=null) sieve.saveScript(filters, true);

	 String scf="";
	 for(String foldername: foldersCache.keySet()) {
	 FolderCache fc=foldersCache.get(foldername);
	 boolean psfon=fc.isScanForcedOn();
	 fc.updateScanFlags();
	 boolean nsfon=fc.isScanForcedOn();
	 if (psfon!=nsfon) {
	 if (scf.length()>0) scf+=",";
	 scf+="{ idfolder: '"+OldUtils.jsEscape(foldername)+"', sfon: "+nsfon+" }";
	 }
	 }

	 sout="{\nresult: true, scf:["+scf+"]\n}";
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } finally {
	 if (stmt!=null) try { stmt.close(); } catch(Exception e) {}
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 out.println(sout);
	 }*/
	// TODO: manage mail filters!!!
/*	public void processManageMailFilters(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 String crud = null;
	 Connection con = null;
	 UserProfile profile = environment.getUserProfile();
	 //WebTopSession wts = environment.getWebTopSession();
		
	 try {
	 crud = ServletUtils.getStringParameter(request, "crud", true);
	 con = this.wtd.getConnection();
			
	 if(crud.equals(Crud.BEGIN)) {
	 EditingSession es = wts.registerEditing(EditingSession.Scope.SESSION, "mailfilters", null, "Regole Messaggi");
	 try {
	 con.setAutoCommit(false);
	 TempTable tt1 = es.addTempTable(con, "mailfilters");
	 TempTable tt2 = es.addTempTable(con, "mailsentfilters");
	 System.out.println(es.getTempTable("mailfilters").tableName);
	 InboxMailFiltersDb.copyToTemp(con, tt1.tableName, profile.getIDDomain(), profile.getUser());
	 SentMailFiltersDb.copyToTemp(con, tt2.tableName, profile.getIDDomain(), profile.getUser());
	 con.commit();
	 new JsonResult(es.id).printTo(out);
	 } catch(SQLException ex1) {
	 con.rollback();
	 wts.unregisterEditing(es);
	 throw ex1;
	 }
					
				
	 } else if(crud.equals(Crud.SAVE)) {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
				
	 try {
	 con.setAutoCommit(false);
	 InboxMailFiltersDb.deleteByDomainUser(con, profile.getIDDomain(), profile.getUser());
	 SentMailFiltersDb.deleteByDomainUser(con, profile.getIDDomain(), profile.getUser());
	 es.copyBackTempTable(con, "mailfilters");
	 es.copyBackTempTable(con, "mailsentfilters");
	 con.commit();
	 new JsonResult().printTo(out);
	 } catch(SQLException ex1) {
	 con.rollback();
	 throw ex1;
	 }
				
	 } else if(crud.equals(Crud.END)) {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
				
	 try {
	 con.setAutoCommit(false);
	 es.removeTempTable(con, "mailfilters");
	 es.removeTempTable(con, "mailsentfilters");
	 con.commit();
	 wts.unregisterEditing(es);
	 new JsonResult().printTo(out);
	 } catch(SQLException ex1) {
	 con.rollback();
	 throw ex1;
	 }
				
				
	 }
	 } catch (EditingException ex) {
	 logger.error("Error managing mail filters", ex);
	 new JsonResult(false, ex.getMessage()).printTo(out);
	 } catch (Exception ex) {
	 logger.error("Error managing mail filters", ex);
	 new JsonResult(false, "Error managing mail filters").printTo(out);
	 }
	 }*/
	// TODO: edit filter
/*    public void processEditFilter(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 UserProfile profile=environment.getUserProfile();
	 String login=profile.getUser();
	 WebTopApp webtopapp=environment.getWebTopApp();
	 String context=request.getParameter("context");
	 String idfilter=request.getParameter("idfilter");
	 //String table="none";
	 //if (context.equals("INBOX")) table="mailfilters";
	 //else if (context.equals("SENT")) table="mailsentfilters";
	 String sout;
	 Connection con=null;
	 Statement stmt=null;
	 ResultSet rs=null;
	 try {
	 String esid = ServletUtils.getStringParameter(request, "esid", true);
	 EditingSession es = wts.getEditing(EditingSession.Scope.SESSION, esid);
	 String table = "none";
	 if (context.equals("INBOX")) table = es.getTempTable("mailfilters").tableName;
	 else if (context.equals("SENT")) table = es.getTempTable("mailsentfilters").tableName;
			
	 con=wtd.getConnection();
	 sout="{\n";
	 stmt=con.createStatement();
	 String sql="select * from "+table+" where iddomain='"+wtd.getDataIDDomain()+"' and login='"+login+"' and idfilter="+idfilter;
	 rs=stmt.executeQuery(sql);
	 if (rs.next()) {
	 sout+="  condition: '"+rs.getString("condition")+"',\n";
	 sout+="  sender: '"+OldUtils.jsEscape(rs.getString("fromvalue"))+"',\n";
	 sout+="  recipient: '"+OldUtils.jsEscape(rs.getString("tovalue"))+"',\n";
	 sout+="  subject: '"+OldUtils.jsEscape(rs.getString("subjectvalue"))+"',\n";
	 sout+="  action: '"+rs.getString("action").toLowerCase()+"',\n";
	 sout+="  actionvalue: '"+OldUtils.jsEscape(rs.getString("actionvalue"))+"',\n";
	 }
	 sout+="  result: true\n}";
	 }  catch(Exception exc) {
	 exc.printStackTrace();
	 sout="{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}";
	 } finally {
	 if (stmt!=null) try { stmt.close(); } catch(Exception e) {}
	 if (con!=null) try { con.close(); } catch(Exception e) {}
	 }
	 out.println(sout);
	 }*/
	boolean checkFileRules(String foldername) {
		boolean b = false;
		Connection con = null;
        //Statement stmt=null;
		//ResultSet rs=null;
		try {
			UserProfile profile = environment.getProfile();
			con = getConnection();
			b = FilterDAO.getInstance().hasFileIntoFolder(con, profile.getDomainId(), profile.getUserId(), foldername);
            //stmt=con.createStatement();
			//rs=stmt.executeQuery(
			//        "select idfilter from mailfilters where iddomain='"+profile.getDomainId()+"' and login='"+profile.getUserId()+"' and "+
			//        " status='E' and action='FILE' and actionvalue='"+OldUtils.getSQLString(foldername)+"'"
			//);
			//b=rs.next();
		} catch (SQLException exc) {
			logger.error("Error checking File rules on folder {}", foldername, exc);
		} finally {
			DbUtils.closeQuietly(con);
		}
		return b;
	}
	
	boolean checkScanRules(String foldername) {
		boolean b = false;
		Connection con = null;
        //Statement stmt=null;
		//ResultSet rs=null;
		try {
			UserProfile profile = environment.getProfile();
			con = getConnection();
			b = ScanDAO.getInstance().isScanFolder(con, profile.getDomainId(), profile.getUserId(), foldername);
            //stmt=con.createStatement();
			//rs=stmt.executeQuery(
			//        "select foldername from mailscan where iddomain='"+profile.getDomainId()+"' and login='"+profile.getUserId()+"' and "+
			//        " foldername='"+OldUtils.getSQLString(foldername)+"'"
			//);
			//b=rs.next();
		} catch (SQLException exc) {
			logger.error("Error checking Scan rules on folder {}", foldername, exc);
		} finally {
			DbUtils.closeQuietly(con);
            //if (rs!=null) try { rs.close(); } catch(SQLException exc) {}
			//if (stmt!=null) try { stmt.close(); } catch(SQLException exc) {}
			//if (con!=null) try { con.close(); } catch(SQLException exc) {}
		}
		return b;
	}
	
	public void processRunAdvancedSearch(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		try {
			if (ast != null && ast.isRunning()) {
				throw new Exception("Advanced search is still running!");
			}
			
			String folder = request.getParameter("folder");
			String ssubfolders = request.getParameter("subfolders");
			String sandor = request.getParameter("andor");
			String sentries[] = request.getParameterValues("entries");
			
			boolean subfolders = ssubfolders.equals("true");
			boolean and = sandor.equals("and");
			
			AdvancedSearchEntry entries[] = new AdvancedSearchEntry[sentries.length];
			for (int i = 0; i < sentries.length; ++i) {
				entries[i] = new AdvancedSearchEntry(sentries[i]);
			}
			
			if (folder.startsWith("folder:")) {
				folder = folder.substring(7);
				ast = new AdvancedSearchThread(this, folder, subfolders, and, entries);
			} else {
				int folderType = folder.equals("personal") ? AdvancedSearchThread.FOLDERTYPE_PERSONAL
						: folder.equals("shared") ? AdvancedSearchThread.FOLDERTYPE_SHARED
								: AdvancedSearchThread.FOLDERTYPE_ALL;
				ast = new AdvancedSearchThread(this, folderType, subfolders, and, entries);
			}
			ast.start();
			out.println("{\nresult: true\n}");
		} catch (Exception exc) {
			exc.printStackTrace();
			out.println("{\nresult: false, text:'" + OldUtils.jsEscape(exc.getMessage()) + "'\n}");
		}
	}
	
	public void processPollAdvancedSearch(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		try {
			String sstart = request.getParameter("start");
			int start = 0;
			if (sstart != null) {
				start = Integer.parseInt(sstart);
			}
			String sout = "{\n";
			if (ast != null) {
				UserProfile profile = environment.getProfile();
				Locale locale = profile.getLocale();
				Calendar cal = Calendar.getInstance(locale);
				ArrayList<Message> msgs = ast.getResult();
				int totalrows = msgs.size();
				int newrows = totalrows - start;
				sout += "totalCount:" + totalrows + ",\nstart:" + start + ",\nlimit:" + newrows + ",\nmessages: [\n";
				boolean first = true;
				for (int i = start; i < msgs.size(); ++i) {
					Message xm = msgs.get(i);
					if (xm.isExpunged()) {
						continue;
					}
					int nid = xm.getMessageNumber();
					IMAPMessage m = (IMAPMessage) xm;
					//Date
					java.util.Date d = m.getSentDate();
					if (d == null) {
						d = m.getReceivedDate();
					}
					if (d == null) {
						d = new java.util.Date(0);
					}
					cal.setTime(d);
					int yyyy = cal.get(Calendar.YEAR);
					int mm = cal.get(Calendar.MONTH);
					int dd = cal.get(Calendar.DAY_OF_MONTH);
					int hhh = cal.get(Calendar.HOUR_OF_DAY);
					int mmm = cal.get(Calendar.MINUTE);
					int sss = cal.get(Calendar.SECOND);
					String xfolder = xm.getFolder().getFullName();
					FolderCache fc = getFolderCache(xfolder);
					String folder = OldUtils.jsEscape(OldUtils.htmlescape(xfolder));
					String foldername = OldUtils.jsEscape(OldUtils.htmlescape(getInternationalFolderName(fc)));
					//From
					String from = "";
					Address ia[] = m.getFrom();
					if (ia != null) {
						InternetAddress iafrom = (InternetAddress) ia[0];
						from = iafrom.getPersonal();
						if (from == null) {
							from = iafrom.getAddress();
						}
					}
					from = (from == null ? "" : OldUtils.jsEscape(OldUtils.htmlescape(from)));
					//To
					String to = "";
					ia = m.getRecipients(Message.RecipientType.TO);
					if (ia != null) {
						InternetAddress iato = (InternetAddress) ia[0];
						to = iato.getPersonal();
						if (to == null) {
							to = iato.getAddress();
						}
					}
					to = (to == null ? "" : OldUtils.jsEscape(OldUtils.htmlescape(to)));
					//Subject
					String subject = m.getSubject();
					if (subject != null) {
						try {
							subject = OldUtils.decodeQString(subject);
						} catch (Exception exc) {
							
						}
					}
					subject = (subject == null ? "" : OldUtils.jsEscape(OldUtils.htmlescape(subject)));
					//Unread
					boolean unread = !m.isSet(Flags.Flag.SEEN);
                    //if (ppattern==null && unread) ++funread;
					//Priority
					int priority = getPriority(m);
					//Status
					java.util.Date today = new java.util.Date();
					Calendar cal1 = Calendar.getInstance(locale);
					Calendar cal2 = Calendar.getInstance(locale);
					boolean isToday = false;
					if (d != null) {
						cal1.setTime(today);
						cal2.setTime(d);
						if (cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
								&& cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
								&& cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) {
							isToday = true;
						}
					}
					
					Flags flags = m.getFlags();
					String status = "read";
					if (flags != null) {
						if (flags.contains(Flags.Flag.ANSWERED)) {
							if (flags.contains("$Forwarded")) {
								status = "repfwd";
							} else {
								status = "replied";
							}
						} else if (flags.contains("$Forwarded")) {
							status = "forwarded";
						} else if (flags.contains(Flags.Flag.SEEN)) {
							status = "read";
						} else if (isToday) {
							status = "new";
						} else {
							status = "unread";
						}
						//                    if (flags.contains(Flags.Flag.USER)) flagImage=webtopapp.getUri()+"/images/themes/"+profile.getTheme()+"/mail/flag.gif";
					}
					//Size
					int msgsize = 0;
					msgsize = (m.getSize() * 3) / 4;// /1024 + 1;
					//User flags
					String cflag = "";
					for (String flagstring : Service.flagStrings) {
						if (!flagstring.equals("complete")) {
							String oldflagstring = "flag" + flagstring;
							if (flags.contains(flagstring)) {
								cflag = flagstring;
							} else if (flags.contains(oldflagstring)) { // vecchio modo
								cflag = flagstring;
							}
						}
					}
					boolean flagComplete = flags.contains("complete");
					if (flagComplete) {
						cflag += "-complete";
					}

                    //idmessage=idmessage.replaceAll("\\\\", "\\\\");
					//idmessage=OldUtils.jsEscape(idmessage);
					if (!first) {
						sout += ",\n";
					}
					boolean archived = false;
					if (profile.hasDocumentManagement()) {
						archived = m.getHeader("X-WT-Archived") != null;
					}
					sout += "{folder:'" + folder + "', folderdesc:'" + foldername + "',idmandfolder:'" + folder + "|" + nid + "',idmessage:'" + nid + "',priority:" + priority + ",status:'" + status + "',to:'" + to + "',from:'" + from + "',subject:'" + subject + "',date: new Date(" + yyyy + "," + mm + "," + dd + "," + hhh + "," + mmm + "," + sss + "),unread: " + unread + ",size:" + msgsize + ",flag:'" + cflag + "'" + (archived ? ",arch:true" : "") + (isToday ? ",istoday:true" : "") + "}";
					first = false;
				}
				sout += "\n]\n, progress: " + ast.getProgress() + ", curfoldername: '" + OldUtils.jsEscape(getInternationalFolderName(ast.getCurrentFolder())) + "', "
						+ "max: " + ast.isMoreThanMax() + ", finished: " + (ast.isFinished() || ast.isCanceled() || !ast.isRunning()) + " }\n";
			} else {
				sout += "totalCount:0,\nstart:0,\nlimit:0,\nmessages: [\n";
				sout += "\n] }\n";
			}
			out.println(sout);
		} catch (Exception exc) {
			exc.printStackTrace();
		}
	}
	
	public void processCancelAdvancedSearch(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
		if (ast != null && ast.isRunning()) {
			ast.cancel();
		}
		out.println("{\nresult: true\n}");
	}

	// TODO: auto save message
/*    public void processAutoSaveMessage(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 try {
	 String newmsgid=request.getParameter("newmsgid");
	 String json=request.getParameter("json");
	 autoSaveData(this.environment,"newmail",newmsgid, json);
	 out.println("{\nresult: true\n}");
	 } catch(Exception exc) {
	 exc.printStackTrace();
	 out.println("{\nresult: false, text:'"+OldUtils.jsEscape(exc.getMessage())+"'\n}");
	 }
	 }*/
	// TODO: get sharing rights
/*	public void processGetSharingRights(HttpServletRequest request, HttpServletResponse response, PrintWriter out) {
	 ArrayList<JsSharingRight> items = new ArrayList<JsSharingRight>();
	 SettingsManager sm = wta.getSettingsManager();
		
	 try {
	 String str = sm.getServiceSetting("mail", Settings.SHARING_RIGHTS);
	 items = LangUtils.fromNameValueString(str, JsSharingRight.class);
	 new JsonResult(items).printTo(out);
			
	 } catch (Exception ex) {
	 new JsonResult(false, ex.getMessage()).printTo(out);
	 logger.error("Error getting sharing rights!", ex);
	 }
	 }*/
	public MailFilters getMailFilters(Connection con, String filtersTable, String login, String iddomain) throws Exception {
		Statement stmt = null;
		ResultSet rs = null;
		
		MailFilters rfilters = null;
		
		try {
			stmt = con.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			
			String sql = "select * from " + filtersTable + " where iddomain='" + iddomain + "' and login='" + login + "' order by idfilter";
			rs = stmt.executeQuery(sql);
			
			MailFilters filters = new MailFilters();

			//Parse filters
			int records = 0;
			while (rs.next()) {
				int idfilter = rs.getInt("idfilter");
				String status = rs.getString("status");
				boolean enabled = status.equals("E");
				String action = rs.getString("action");
				String actionvalue = rs.getString("actionvalue");
				String operator = "or";
				if (rs.getString("condition").equals("ALL")) {
					operator = "and";
				}
				
				MailFilterConditions mfcs = new MailFilterConditions(idfilter, enabled, action, actionvalue, operator);
				
				String fromvalue = rs.getString("fromvalue");
				if (fromvalue != null && fromvalue.trim().length() > 0) {
					MailFilterCondition mfc = new MailFilterCondition();
					mfc.setComparison(MailFilterCondition.CONTAINS);
					mfc.setField("from");
					mfc.setValues(fromvalue, true);
					mfcs.add(mfc);
				}
				String tovalue = rs.getString("tovalue");
				if (tovalue != null && tovalue.trim().length() > 0) {
					MailFilterCondition mfc = new MailFilterCondition();
					mfc.setComparison(MailFilterCondition.CONTAINS);
					mfc.setField("to");
					mfc.setValues(tovalue, true);
					mfcs.add(mfc);
				}
				String subjectvalue = rs.getString("subjectvalue");
				if (subjectvalue != null && subjectvalue.trim().length() > 0) {
					MailFilterCondition mfc = new MailFilterCondition();
					mfc.setComparison(MailFilterCondition.CONTAINS);
					mfc.setField("subject");
					mfc.setValues(subjectvalue, false);
					mfcs.add(mfc);
				}
				String sizevalue = rs.getString("sizevalue");
				String size = rs.getString("sizematch");
				if (size != null && size.trim().length() > 0 && sizevalue != null && sizevalue.trim().length() > 0) {
					MailFilterCondition mfc = new MailFilterCondition();
					if (size.equals("+")) {
						mfc.setComparison(MailFilterCondition.GREATERTHAN);
					} else {
						mfc.setComparison(MailFilterCondition.LESSTHAN);
					}
					mfc.setField("size");
					mfc.setValues(sizevalue, true);
					mfcs.add(mfc);
				}
				String fieldvalue = rs.getString("fieldvalue");
				String fieldname = rs.getString("fieldname");
				if (fieldname != null && fieldname.trim().length() > 0 && fieldvalue != null && fieldvalue.trim().length() > 0) {
					MailFilterCondition mfc = new MailFilterCondition();
					mfc.setComparison(MailFilterCondition.CONTAINS);
					mfc.setField(fieldname);
					mfc.setValues(fieldvalue, true);
					mfcs.add(mfc);
				}
				
				filters.add(mfcs);
			}
			rs.close();

			//Parse vacation
			sql = "select * from vacation where iddomain='" + iddomain + "' and login='" + login + "'";
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				boolean vactive = rs.getString("active").equals("Y");
				String message = rs.getString("message");
				String addresses = rs.getString("addresses");
				if (vactive) {
					filters.setVacation(message, addresses);
				}
			}
			rs.close();
			stmt.close();
			
			rfilters = filters;
			
		} catch (SQLException exc) {
			logger.error("Error saving Sieve script", exc);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException exc2) {
				}
			}
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException exc2) {
				}
			}
		}
		
		return rfilters;
	}
	
	public boolean isSharedSeen() throws MessagingException {
		SonicleIMAPFolder xfolder = (SonicleIMAPFolder) store.getFolder("INBOX");
		String annot = xfolder.getAnnotation("/vendor/cmu/cyrus-imapd/sharedseen", true);
		return annot.equals("true");
	}
	
	public void setSharedSeen(boolean b) throws MessagingException {
		SonicleIMAPFolder xfolder = (SonicleIMAPFolder) store.getFolder("INBOX");
		xfolder.setAnnotation("/vendor/cmu/cyrus-imapd/sharedseen", true, b ? "true" : "false");
	}

	// TODO: set folder sharing!
/*	public void setFolderSharing(String targetUser, String resource, String params) throws MessagingException {
	 String acluser=wtd.getMailUsername(targetUser);
	 if (acluser==null) return;
		
	 FolderCache fc=null;
	 if (!resource.equals("INBOX")) {
	 fc=getFolderCache(resource);
	 if (fc==null) return;
	 }
		
	 ACL acl=new ACL(acluser,new Rights(params));
	 if (fc!=null) {
	 IMAPFolder folder=(IMAPFolder)fc.getFolder();
	 _setFolderSharing(folder,acl);
	 }
	 else {
	 Folder folder=getDefaultFolder();
	 Folder children[]=folder.list();
	 for(Folder child: children)
	 _setFolderSharing((IMAPFolder)child,acl);
	 }
		
	 }
	
	 private void _setFolderSharing(IMAPFolder folder, ACL acl) throws MessagingException {
	 if (!isLeaf(folder)) {
	 Folder children[]=folder.list();
	 for(Folder child: children)
	 _setFolderSharing((IMAPFolder)child, acl);
	 }
	 //logger.debug("set sharing on {} to {}",folder.getFullName(),acl.getName());
	 folder.removeACL(acl.getName());
	 folder.addACL(acl);			
	 }
	
	 public void removeFolderSharing(String targetUser, String resource) throws MessagingException {
	 logger.debug("removeFolderSharing({},{})",targetUser,resource);
	 String acluser=wtd.getMailUsername(targetUser);
	 if (acluser==null) return;
		
	 FolderCache fc=null;
	 if (!resource.equals("INBOX")) {
	 fc=getFolderCache(resource);
	 if (fc==null) return;
	 }
		
	 if (fc!=null) {
	 IMAPFolder folder=(IMAPFolder)fc.getFolder();
	 _removeFolderSharing(folder,acluser);
	 }
	 else {
	 Folder folder=getDefaultFolder();
	 Folder children[]=folder.list();
	 for(Folder child: children)
	 _removeFolderSharing((IMAPFolder)child,acluser);
	 }
				
	 }
	
	 private void _removeFolderSharing(IMAPFolder folder, String acluser) throws MessagingException {
	 if (!isLeaf(folder)) {
	 Folder children[]=folder.list();
	 for(Folder child: children)
	 _removeFolderSharing((IMAPFolder)child, acluser);
	 }
	 logger.debug("remove sharing from {} to {}",folder.getFullName(),acluser);
	 folder.removeACL(acluser);
	 }*/
	public Principal getPrincipal(String domainId, String mailUserId) {
		/*        Connection con=null;
		 String iddomain=wtd.getLocalIDDomain();
		 String dlogin=mailusername;
		 String dname=mailusername;
		 AuthenticationDomain ad=getAuthenticationDomain(iddomain);
		 try {
		 con=getMainConnection();
		 stmt=con.createStatement();
		 rs=stmt.executeQuery("select login,username from users where iddomain='"+iddomain+"' and mailusername='"+mailusername+"'");
		 if (rs.next()) {
		 dlogin=rs.getString("login");
		 dname=rs.getString("username");
		 }
		 else if (wtd.isLdap()) {
		 rs.close();
		 rs=stmt.executeQuery("select login,username from users where iddomain='"+iddomain+"' and login='"+mailusername+"'");
		 if (rs.next()) {
		 dlogin=rs.getString("login");
		 dname=rs.getString("username");
		 }
		 }
		 } catch(SQLException exc) {
		 exc.printStackTrace();
		 } finally {
		 if (rs!=null) try { rs.close(); } catch(Exception exc) {}
		 if (stmt!=null) try { stmt.close(); } catch(Exception exc) {}
		 if (con!=null) try { con.close(); } catch(Exception exc) {}
		 }
		 com.sonicle.security.acl.Principal p=new com.sonicle.security.acl.Principal(dlogin,ad,dname);*/
		Principal p = null;
		Connection con = null;
		try {
			con = getConnection();
			logger.debug("looking for shared folder map on {}@{}",mailUserId,domainId);
			OUserMap omap=UserMapDAO.getInstance().selectFirstByMailUser(con, domainId, mailUserId);
			OUser ouser;
			if (omap!=null) {
				logger.debug("found mapping : {}",omap.getUserId());
				//get mapped webtop user
				ouser=UserDAO.getInstance().selectByDomainUser(con, domainId, omap.getUserId());
			} else {
				logger.debug("mapping not found, looking for a webtop user with id = {}",mailUserId);
				//try looking for a webtop user with userId=mailUserId
				ouser=UserDAO.getInstance().selectByDomainUser(con, domainId, mailUserId);
			}
			
			if (ouser!=null) {
				String desc=LangUtils.value(ouser.getFirstName(),"")+" "+LangUtils.value(ouser.getLastName(),"");
				desc=desc.trim();
				logger.debug("webtop user found, desc={}",desc);
				p = new Principal(mailUserId, AuthenticationDomain.getInstance(con, domainId), desc);
			} else {
				logger.debug("webtop user not found, creating unmapped principal");
				p = new Principal(mailUserId, AuthenticationDomain.getInstance(con, domainId), mailUserId);
			}
			
		} catch (SQLException exc) {
			logger.error("Error finding principal for {}@{}", mailUserId, domainId, exc);
		} finally {
			DbUtils.closeQuietly(con);
		}
		return p;
	}
	
	private void createFile(InputStream in, File outfile) throws IOException {
		FileOutputStream out = new FileOutputStream(outfile);
		byte buffer[] = new byte[8192];
		int n = 0;
		while ((n = in.read(buffer)) >= 0) {
			if (n > 0) {
				out.write(buffer, 0, n);
			}
		}
		out.close();
	}
	
	private void fastStreamCopy(final InputStream src, final OutputStream dest) throws IOException {
		final ReadableByteChannel in = Channels.newChannel(src);
		final WritableByteChannel out = Channels.newChannel(dest);		
		fastChannelCopy(in, out);
		in.close();
		out.close();
	}
	
	public static void fastChannelCopy(final ReadableByteChannel src, final WritableByteChannel dest) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);
		while (src.read(buffer) != -1) {
			// prepare the buffer to be drained
			buffer.flip();
			// write to the channel, may block
			dest.write(buffer);
	      // If partial transfer, shift remainder down
			// If buffer is empty, same as doing clear()
			buffer.compact();
		}
		// EOF will leave buffer in fill state
		buffer.flip();
		// make sure the buffer is fully drained.
		while (buffer.hasRemaining()) {
			dest.write(buffer);
		}
	}
	
}