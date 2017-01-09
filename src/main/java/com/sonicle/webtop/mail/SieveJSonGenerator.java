/*
* WebTop Groupware is a bundle of WebTop Services developed by Sonicle S.r.l.
* Copyright (C) 2011 Sonicle S.r.l.
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

import com.sonicle.commons.MailUtils;
import java.sql.*;
import java.util.ArrayList;
import java.util.Locale;

import com.sonicle.mail.sieve.*;
import org.apache.commons.lang3.StringEscapeUtils;

/**
 *
 * @author gbulfon
 */
public class SieveJSonGenerator implements MailRulesParserListener {

        String context;
        StringBuffer json=new StringBuffer();
        Service ms;
        Locale locale;
        String prefix;

        public SieveJSonGenerator(String context, Service ms, Locale locale, String folderPrefix) {
            this.context=context;
            this.ms=ms;
            this.locale=locale;
            this.prefix=folderPrefix;
        }

        public StringBuffer generate(MailRules rules) throws SQLException {
            json.append("{\n");
            MailRulesParser.parse(rules,this);
            return json;
        }

        public void rulesStart() {
            json.append("  rules: [\n");
        }

        public void rule(int row, MailRuleConditions mrcs) {
            if (row>0) json.append(",\n");
            
			long rule_id=mrcs.getRuleId();
			boolean enabled=mrcs.isEnabled();
			String operator=mrcs.getOperator();
			String action=mrcs.getAction();
			String actionvalue=mrcs.getActionValue();
			
            //String saction=StringEscapeUtils.escapeEcmaScript(ms.lookupResource(locale, MailLocaleKey.RULES_ACTION(action.toLowerCase())));
            //String sactionvalue=" ";
            //if (actionvalue!=null && actionvalue.trim().length()>0) {
            //    if (action.equals("FILE")) {
            //        if (actionvalue.startsWith(prefix)) sactionvalue=actionvalue.substring(prefix.length());
            //    } else if (action.equals("REJECT")) {
            //        sactionvalue=actionvalue;
            //    }
            //}
            //sactionvalue=StringEscapeUtils.escapeEcmaScript(sactionvalue);
			
			String condition=mrcs.getOperator().equals("or")?"ANY":"ALL";

			String rjson="    {"+
					" rule_id: "+rule_id+","+
					" active: "+enabled+","+
					" condition: '"+condition+"',"+
					" action: '"+action+"',"+
					" value: '"+actionvalue+"',";
			
			int mfcssize=mrcs.size();
            String description=ms.lookupResource(locale,MailLocaleKey.RULES_IF)+" ";
            for(int i=0;i<mfcssize;++i) {
                if (i>0) description+=" "+ms.lookupResource(locale,MailLocaleKey.RULES_OPERATOR(operator))+" ";
                MailRuleCondition mrc=mrcs.get(i);
				String field=mrc.getField();
				String stringComparison=mrc.getStringComparison();
                description+=ms.lookupResource(locale,MailLocaleKey.RULES_FIELD(field))+" ";
                description+=ms.lookupResource(locale,MailLocaleKey.RULES_COMPARISON(stringComparison))+" ";
                ArrayList<String> values=mrc.getValues();
                int vsize=values.size();
                if (vsize>1) description+="(";
                for(int v=0;v<vsize;++v) {
                    if (v>0) description+=" "+ms.lookupResource(locale,MailLocaleKey.RULES_OR)+" ";
                    String value=values.get(v);
                    description+="\""+value+"\"";
                }
                if (vsize>1) description+=")";
				
				rjson+=" "+field+": '"+StringEscapeUtils.escapeEcmaScript(mrc.getStringValues())+"',";
            }
            description=StringEscapeUtils.escapeEcmaScript(description);
			
			rjson+=" description: '"+description+"' }";


            json.append(rjson);
        }

        public void rulesEnd(int rows) {
            json.append("  ],\n");
            json.append("  totalCount: "+rows+"\n");
            json.append("}\n");
        }
        
        public void vacation(boolean active, String message, String addresses) {
/*            if (message!=null) {
                json.append("  vactive: "+active+",\n");
                json.append("  vmessage: '"+Utils.jsEscape(message)+"',\n");
                json.append("  vaddresses: '"+Utils.jsEscape(addresses)+"'\n");
            } else {
                json.append("  vactive: false,\n");
                json.append("  vmessage: '',\n");
                json.append("  vaddresses: ''\n");
            }
            json.append("}\n");*/
        }
		
}
