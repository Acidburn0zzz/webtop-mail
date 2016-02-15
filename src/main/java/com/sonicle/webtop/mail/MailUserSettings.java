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
import com.sonicle.webtop.core.sdk.BaseUserSettings;
import com.sonicle.webtop.core.sdk.UserProfile;
import java.text.MessageFormat;
import java.util.HashMap;

/**
 *
 * @author gbulfon
 */
public class MailUserSettings extends BaseUserSettings {

	public static final String MESSAGE_QUICKPART = "message.quickpart@{0}"; // !IMPORTANT
	public static final String MESSAGE_LIST_GROUP = "messagelist.group@{0}"; // was : "messagelist-group-{0}"
	public static final String MESSAGE_LIST_SORT = "messagelist.sort@{0}"; // was : "messagelist-{0}-sort"
	public static final String MESSAGE_LIST_THREADED = "messagelist.threaded@{0}"; // was : "list-threaded-{0}"
	public static final String COLUMN_SIZE_PREFIX = "column.size@"; //was : "column-{0}"
	public static final String COLUMN_SIZE = COLUMN_SIZE_PREFIX+"{0}"; //was : "column-{0}"
	public static final String COLUMN_VISIBLE = "column.visible@{0}";
	public static final String COLUMNS_ORDER = "columns.order";
	public static final String SHARED_SEEN = "sharedseen";
	public static final String SHARING_RIGHTS = "sharing.rights";
	public static final String FOLDER_PEFFIX = "folder.prefix";
	public static final String SCAN_ALL = "scan.all";
	public static final String SCAN_SECONDS = "scan.seconds";
	public static final String SCAN_CYCLES = "scan.cycles";
	public static final String FOLDER_SENT = "folder.sent";
	public static final String FOLDER_DRAFTS = "folder.drafts";
	public static final String FOLDER_TRASH = "folder.trash";
	public static final String FOLDER_SPAM = "folder.spam";
	public static final String DEFAULT_FOLDER = "defaultfolder";
	public static final String REPLY_TO = "reply.to";
	public static final String SHARED_SORT = "shared.sort";
	public static final String INCLUDE_MESSAGE_IN_REPLY = "include.message.in.reply";
	public static final String PAGE_ROWS = "page.rows";
	public static final String HOST="host";
	public static final String PORT="port";
	public static final String USERNAME="username";
	public static final String PASSWORD="password";
	public static final String PROTOCOL="protocol";
	public static final String MESSAGE_VIEW_REGION = "message.view.region";
	public static final String MESSAGE_VIEW_WIDTH = "message.view.width";
	public static final String MESSAGE_VIEW_HEIGHT = "message.view.height";
	public static final String MESSAGE_VIEW_COLLAPSED = "message.view.collapsed";
    
	private MailServiceSettings mss; //TODO: portare le chiavi di default qui?
	
	public MailUserSettings(UserProfile.Id profileId) {
		super("com.sonicle.webtop.mail",profileId);
	}
	
	public MailUserSettings(UserProfile.Id profileId, MailServiceSettings mss) {
		super("com.sonicle.webtop.mail",profileId);
		this.mss=mss;
	}
	
	/**
	 * [string]
	 * Archiving operative method. One of: simple, structured, webtop.
	 * A null value indicated no method.
	 */
	public static final String ARCHIVING_METHOD = "archiving.method";
	
	public static final String ARCHIVING_METHOD_NONE = "none";
	public static final String ARCHIVING_METHOD_SIMPLE = "simple";
	public static final String ARCHIVING_METHOD_STRUCTURED = "structured";
	public static final String ARCHIVING_METHOD_WEBTOP = "webtop";
	
	/**
	 * [string]
	 * IMAP folder to be monitored by the archiving process
	 */
	public static final String SIMPLE_ARCHIVING_MAIL_FOLDER = "archiving.simple.mailfolder";
	
	public String getSimpleArchivingMailFolder() {
		return getString(SIMPLE_ARCHIVING_MAIL_FOLDER, null);
	}
	
	public boolean setSimpleArchivingMailFolder(String value) {
		return setString(SIMPLE_ARCHIVING_MAIL_FOLDER, value);
	}
	
	public String getArchivingMethod() {
		return getString(ARCHIVING_METHOD, "none");
	}
	
	public boolean setArchivingMethod(String value) {
		return setString(ARCHIVING_METHOD, value);
	}
	
    
    public boolean isSharedSeen() {
        return getBoolean(SHARED_SEEN, false);
    }
	
	public String getMessageListGroup(String foldername) {
		return getString(MessageFormat.format(MESSAGE_LIST_GROUP, foldername),"");
	}
	
	public void setMessageListGroup(String foldername, String group) {
		setString(MessageFormat.format(MESSAGE_LIST_GROUP, foldername),group);
	}
	
	public int getColumnSize(String name) {
		return getInteger(MessageFormat.format(COLUMN_SIZE,name),100);
	}
	
	public void setColumnSize(String name, int size) {
		setInteger(MessageFormat.format(COLUMN_SIZE,name),size);
	}
	
	public ColumnsOrderSetting getColumnsOrderSetting() {
		return LangUtils.value(getSetting(COLUMNS_ORDER),new ColumnsOrderSetting(), ColumnsOrderSetting.class);
	}
	
	public void setColumnsOrderSetting(ColumnsOrderSetting cos) {
		setObject(COLUMNS_ORDER,cos,ColumnsOrderSetting.class);
	}
    
	public void clearColumnsOrderSetting() {
		clear(COLUMNS_ORDER);
	}
	
	public ColumnVisibilitySetting getColumnVisibilitySetting(String foldername) {
		return LangUtils.value(
				getSetting(MessageFormat.format(COLUMN_VISIBLE, foldername)),
				new ColumnVisibilitySetting(), ColumnVisibilitySetting.class
		);
	}
	
	public void setColumnVisibilitySetting(String foldername, ColumnVisibilitySetting cvs) {
		setObject(MessageFormat.format(COLUMN_VISIBLE, foldername),cvs,ColumnVisibilitySetting.class);
	}
    
	public void clearColumnVisibilitySetting(String foldername) {
		clear(MessageFormat.format(COLUMN_VISIBLE, foldername));
	}
	
	public String getMessageListSort(String foldername) {
		return getString(MessageFormat.format(MESSAGE_LIST_SORT, foldername),"date|DESC");
	}
	
	public void setMessageListSort(String foldername, String value) {
		setString(MessageFormat.format(MESSAGE_LIST_SORT, foldername),value);
	}
	
	public boolean isMessageListThreaded(String foldername) {
		return getBoolean(MessageFormat.format(MESSAGE_LIST_THREADED, foldername),false);
	}
	
	public void setMessageListThreaded(String foldername, boolean threaded) {
		setBoolean(MessageFormat.format(MESSAGE_LIST_THREADED, foldername),threaded);
	}
	
	public String getFolderPrefix() {
		return getString(FOLDER_PEFFIX, mss.getDefaultFolderPrefix());
	}
	
	public boolean isScanAll() {
		return getBoolean(SCAN_ALL,mss.isDefaultScanAll());
	}
	
	public int getScanSeconds() {
		return getInteger(SCAN_SECONDS,mss.getDefaultScanSeconds());
	}
	
	public int getScanCycles() {
		return getInteger(SCAN_CYCLES,mss.getDefaultScanCycles());
	}
	
	public String getFolderSent() {
		return getString(FOLDER_SENT,mss.getDefaultFolderSent());
	}
	
	public String getFolderDrafts() {
		return getString(FOLDER_DRAFTS,mss.getDefaultFolderDrafts());
	}
	
	public String getFolderTrash() {
		return getString(FOLDER_TRASH,mss.getDefaultFolderTrash());
	}

	public String getFolderSpam() {
		return getString(FOLDER_SPAM,mss.getDefaultFolderSpam());
	}
	
	public String getReplyTo() {
		return getString(REPLY_TO, null);
	}
	
	public String getSharedSort() {
		return getString(SHARED_SORT,"N");
	}
	
	public boolean isIncludeMessageInReply() {
		return getBoolean(INCLUDE_MESSAGE_IN_REPLY, mss.isDefaultIncludeMessageInReply());
	}
	
	public int getPageRows() {
		return getInteger(PAGE_ROWS,mss.getDefaultPageRows());
	}
	
	public void setPageRows(int rows) {
		setInteger(PAGE_ROWS, rows);
	}
	
	public String getHost() {
		return getString(HOST,null);
	}
	
	public void setHost(String host) {
		setString(HOST, host);
	}
	
	public int getPort() {
		return getInteger(PORT,0);
	}
	
	public void setPort(int port) {
		setInteger(PORT, port);
	}
	
	public String getUsername() {
		return getString(USERNAME,null);
	}
	
	public void setUsername(String username) {
		setString(USERNAME, username);
	}
	
	public String getPassword() {
		return getString(PASSWORD,null);
	}
	
	public void setPassword(String password) {
		setString(PASSWORD, password);
	}
	
	public String getProtocol() {
		return getString(PROTOCOL,null);
	}
	
	public void setProtocol(String protocol) {
		setString(PROTOCOL, protocol);
	}
	
	public String getMessageViewRegion() {
		return getString(MESSAGE_VIEW_REGION,"south");
	}
	
	public void setMessageViewRegion(String region) {
		setString(MESSAGE_VIEW_REGION,region);
	}
	
	public int getMessageViewWidth() {
		return getInteger(MESSAGE_VIEW_WIDTH,640);
	}
	
	public void setMessageViewWidth(int width) {
		setInteger(MESSAGE_VIEW_WIDTH, width);
	}
	
	public int getMessageViewHeight() {
		return getInteger(MESSAGE_VIEW_HEIGHT,400);
	}
	
	public void setMessageViewHeight(int height) {
		setInteger(MESSAGE_VIEW_HEIGHT,height);
	}
	
	public boolean getMessageViewCollapsed() {
		return getBoolean(MESSAGE_VIEW_COLLAPSED,false);
	}
	
	public void setMessageViewCollapsed(boolean collapsed) {
		setBoolean(MESSAGE_VIEW_COLLAPSED,collapsed);
	}
	
	public String getDefaultFolder() {
		return getString(DEFAULT_FOLDER,null);
	}
	
	public HashMap<String,Integer> getColumnSizes() {
		return getIntegers(COLUMN_SIZE_PREFIX);
	}
	
}
