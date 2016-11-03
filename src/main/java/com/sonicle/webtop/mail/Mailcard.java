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

import com.sonicle.webtop.core.sdk.UserPersonalInfo;
import java.util.regex.Pattern;

/**
 *
 * @author gabriele.bulfon
 */
public class Mailcard {
	
	public static final Pattern PATTERN_TITLE = Pattern.compile("\\{TITLE\\}");
	public static final Pattern PATTERN_FIRST_NAME = Pattern.compile("\\{FIRST_NAME\\}");
	public static final Pattern PATTERN_LAST_NAME = Pattern.compile("\\{LAST_NAME\\}");
	public static final Pattern PATTERN_COMPANY = Pattern.compile("\\{COMPANY\\}");
	public static final Pattern PATTERN_FUNCTION = Pattern.compile("\\{FUNCTION\\}");
	public static final Pattern PATTERN_WORK_EMAIL = Pattern.compile("\\{EMAIL\\}");
	public static final Pattern PATTERN_WORK_MOBILE = Pattern.compile("\\{MOBILE\\}");
	public static final Pattern PATTERN_WORK_TELEPHONE = Pattern.compile("\\{TELEPHONE\\}");
	public static final Pattern PATTERN_WORK_FAX = Pattern.compile("\\{FAX\\}");
	public static final Pattern PATTERN_CUSTOM_1 = Pattern.compile("\\{CUSTOM_1\\}");
	public static final Pattern PATTERN_CUSTOM_2 = Pattern.compile("\\{CUSTOM_2\\}");
	public static final Pattern PATTERN_CUSTOM_3 = Pattern.compile("\\{CUSTOM_3\\}");
	
	public static final String TYPE_EMPTY = "empty";
	public static final String TYPE_DEFAULT = "default";
	public static final String TYPE_USER = "user";
	public static final String TYPE_EMAIL = "email";
	public static final String TYPE_EMAIL_DOMAIN = "emaildomain";
	
	public String source = Mailcard.TYPE_EMPTY;
	public String html = "<br>";
	
	public Mailcard() {
		
	}
	
	public Mailcard(String source, String html) {
		this.source = source;
		this.html = html;
	}
	
	public void substitutePlaceholders(UserPersonalInfo personalInfo) {
		this.html = Mailcard.replaceTemplateFields(this.html, personalInfo);
	}
	
	public static String replaceTemplateFields(String html, UserPersonalInfo personalInfo) {
		if(personalInfo != null) {
			String title=personalInfo.getTitle();
			String firstName=personalInfo.getFirstName();
			String lastName=personalInfo.getLastName();
			String company=personalInfo.getCompany();
			String function=personalInfo.getFunction();
			String email=personalInfo.getEmail();
			String mobile=personalInfo.getMobile();
			String telephone=personalInfo.getTelephone();
			String fax=personalInfo.getFax();
			String custom1=personalInfo.getCustom01();
			String custom2=personalInfo.getCustom02();
			String custom3=personalInfo.getCustom03();
			if(title != null) html = PATTERN_TITLE.matcher(html).replaceAll(title);
			if(firstName != null) html = PATTERN_FIRST_NAME.matcher(html).replaceAll(firstName);
			if(lastName != null) html = PATTERN_LAST_NAME.matcher(html).replaceAll(lastName);
			if(company != null) html = PATTERN_COMPANY.matcher(html).replaceAll(company);
			if(function != null) html = PATTERN_FUNCTION.matcher(html).replaceAll(function);
			if(email != null) html = PATTERN_WORK_EMAIL.matcher(html).replaceAll(email);
			if(mobile != null) html = PATTERN_WORK_MOBILE.matcher(html).replaceAll(mobile);
			if(telephone != null) html = PATTERN_WORK_TELEPHONE.matcher(html).replaceAll(telephone);
			if(fax != null) html = PATTERN_WORK_FAX.matcher(html).replaceAll(fax);
			if(custom1 != null) html = PATTERN_CUSTOM_1.matcher(html).replaceAll(custom1);
			if(custom2 != null) html = PATTERN_CUSTOM_2.matcher(html).replaceAll(custom2);
			if(custom3 != null) html = PATTERN_CUSTOM_3.matcher(html).replaceAll(custom3);
		}
		return html;
	}
}