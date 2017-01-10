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
package com.sonicle.webtop.mail.dal;

import com.sonicle.webtop.core.dal.BaseDAO;
import com.sonicle.webtop.core.dal.DAOException;
import com.sonicle.webtop.mail.bol.ORule;
import java.sql.Connection;
import org.jooq.DSLContext;
import static com.sonicle.webtop.mail.jooq.Tables.*;
import com.sonicle.webtop.mail.jooq.tables.records.RulesRecord;
import java.util.List;

/**
 *
 * @author gbulfon
 */
public class RuleDAO extends BaseDAO {
	
	private final static RuleDAO INSTANCE = new RuleDAO();
	public static RuleDAO getInstance() {
		return INSTANCE;
	}
	
	public boolean hasFileIntoFolder(Connection con, String domainId, String userId, String foldername) throws DAOException {
		DSLContext dsl = getDSL(con);
		return dsl
			.select()
			.from(RULES)
			.where(
				RULES.DOMAIN_ID.equal(domainId)
				.and(RULES.USER_ID.equal(userId)
				.and(RULES.STATUS.equal("E")
				.and(RULES.ACTION.equal("FILE")
				.and(RULES.ACTION_VALUE.equal(foldername)))))
			)
			.limit(1)
			.fetchOneInto(ORule.class)!=null;
	}
	
	public List<ORule> selectByUserId(Connection con, String domainId, String userId) throws DAOException {
		DSLContext dsl = getDSL(con);
		return dsl
			.select()
			.from(RULES)
			.where(
				RULES.DOMAIN_ID.equal(domainId)
				.and(RULES.USER_ID.equal(userId))
			)
			.orderBy(RULES.RULE_ID)
			.fetchInto(ORule.class);
	}
	
	public int insert(Connection con, ORule item) throws DAOException {
		DSLContext dsl = getDSL(con);
		RulesRecord record = dsl.newRecord(RULES, item);
		return dsl
			.insertInto(RULES)
			.set(record)
			.execute();
	}	

	public int deleteByUserId(Connection con, String domainId, String userId) throws DAOException {
		DSLContext dsl = getDSL(con);
		return dsl
			.delete(RULES)
			.where(RULES.DOMAIN_ID.equal(domainId)
					.and(RULES.USER_ID.equal(userId))
			)
			.execute();
	}
	
}
