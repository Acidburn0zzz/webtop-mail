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

Ext.define('Sonicle.webtop.mail.view.AdvancedSearchDialog', {
	extend: 'WTA.sdk.DockableView',
	
	dockableConfig: {
		iconCls: 'wt-icon-search-adv-xs',
		title: '{act-advsearch.lbl}',
		width: 700,
		height: 600
	},
	promptConfirm: false,
	full: true,
	
	mys: null,
	
	folder: null,
	folderText: null,
	folderHasChildren: false,

    searchRunning: false,
    searchButton: null,
    searchCombo: null,
	searchTrashSpam: null,
    searchCheckbox: null,
    searchToolbar: null,
    searchGrid: null,
    messageGrid: null,
    searchLabel: null,
    searchProgress: null,
    
	initComponent: function() {
		var me = this;
		
		me.searchToolbar=new Ext.Toolbar({
			items: [
				Ext.create("Ext.form.Label", { text: me.res('advsearch-folder')+':' }),
				' ',
				me.searchCombo=Ext.create("Ext.form.ComboBox",{
					forceSelection: true,
					width: 300,
					listWidth: 300,
					mode: 'local',
					displayField: 'desc',
					triggerAction: 'all',
					//selectOnFocus: true,
					editable: false,
					store: new Ext.data.SimpleStore({
						fields: ['id', 'desc'],
						data: [
							[ 'folder:'+me.folder, me.folderText ],
							[ 'all', me.res('advsearch-allfolders')],
							[ 'personal', me.res('advsearch-personalfolders')],
							[ 'shared', me.res('advsearch-sharedfolders')]
						]
					}),
					value: (me.folder==me.mys.getFolderInbox())?'all':'folder:'+me.folder,
					valueField: 'id',
					listeners: {
						'select': {
							fn: function(cb,r,ix) {
							  if (r.get('id').startsWith('folder:')) me.searchCheckbox.show();
							  else me.searchCheckbox.hide();
							}
						}
					}
				}),
				'-',
				me.searchTrashSpam=Ext.create('Ext.form.Checkbox',{ boxLabel: me.res('advsearch-searchtrashspam'), checked: false }),
				'->',
				me.searchCheckbox=Ext.create('Ext.form.Checkbox',{ boxLabel: me.res('advsearch-searchsubfolders'), checked: me.folderHasChildren, disabled: !me.folderHasChildren}),'->'
			]
		});
			
		Ext.apply(me, {
			
			tbar: me.searchToolbar,		
			buttons: [
				me.searchButton=Ext.create('Ext.Button',{ 
					width: 100, 
					text: WT.res('word.search'), 
					handler: me.runstopSearch, 
					scope: me 
				}),
				' ',
				me.searchLabel=Ext.create('Ext.form.Label',{ cls: 'x-mask-msg-text', width: 30, hidden: true }),
				' ',
				me.searchProgress=Ext.create('Ext.form.Label',{ width: 200, hidden: true, text: '0%' }),
				'->',
				Ext.create('Ext.Button',{ 
					width: 100, 
					text: me.res('advsearch-savesearchfolder'), 
					disabled: true
				}),
				' ',
				Ext.create('Ext.Button',{
					width: 100, 
					text: WT.res('act-close.lbl'), 
					handler: function() {
						me.closeView();
					}
				})
			]
		});
		
		me.callParent(arguments);
		
		me.add(
			me.searchGrid=Ext.create('Sonicle.webtop.mail.view.AdvancedSearchGrid',{
				mys: me.mys,
				autoScroll: true,
				region: 'north',
				height: 250,
				split: true,
				searchfields: [
					[ 'subject', me.res('subject')],
					[ 'from', me.res('from')],
					[ 'to', me.res('to')],
					[ 'cc', me.res('cc')],
					[ 'tocc', me.res('tocc')],
					[ 'alladdr', me.res('fromtoccbcc')],
					[ 'date', me.res('date')],
					[ 'body', me.res('message')],
					[ 'priority', me.res('priority')],
					[ 'status', me.res('status')],
					[ 'flags', me.res('flags')],
					[ 'any', me.res('anywhere')]
				],
				searchdata: {
					'subject': null,
					'from': null,
					'to': null,
					'cc': null,
					'tocc': null,
					'alladdr': null,
					'date': {
						xtype: 'datefield'
					},
					'body': null,
					'priority': {
						xtype: 'combo',
						forceSelection: true,
						mode: 'local',
						displayField: 'desc',
						triggerAction: 'all',
						//selectOnFocus: true,
						editable: false,
						store: new Ext.data.SimpleStore({
							fields: ['id','desc'],
							data: [
								['1',me.res('high')],
								['3',me.res('normal')]
							]
						}),
						//value: this.method,
						valueField: 'id'
					},
					'status': {
						xtype: 'soiconcombo',
						forceSelection: true,
						mode: 'local',
						displayField: 'desc',
						iconClsField: 'icon',
						triggerAction: 'all',
						//selectOnFocus: true,
						editable: false,
						store: new Ext.data.SimpleStore({
							fields: ['id','desc','icon'],
							data: [
								['replied',me.res('streplied'),'wtmail-icon-status-replied-xs'],
								['forwarded',me.res('stforwarded'),'wtmail-icon-status-forwarded-xs'],
								['read',me.res('stread'),'wtmail-icon-status-read-xs'],
								['new',me.res('strecent'),'wtmail-icon-status-new-xs']
							]
						}),
						//value: this.method,
						valueField: 'id'
					},
					'flags': {
						xtype: 'soiconcombo',
						forceSelection: true,
						mode: 'local',
						displayField: 'desc',
						iconClsField: 'icon',
						triggerAction: 'all',
						//selectOnFocus: true,
						editable: false,
						store: new Ext.data.SimpleStore({
							fields: ['id','desc','icon'],
							data: [
								['red',me.res('flagred'),'wtmail-icon-flagred-xs'],
								['blue',me.res('flagblue'),'wtmail-icon-flagblue-xs'],
								['yellow',me.res('flagyellow'),'wtmail-icon-flagyellow-xs'],
								['green',me.res('flaggreen'),'wtmail-icon-flaggreen-xs'],
								['orange',me.res('flagorange'),'wtmail-icon-flagorange-xs'],
								['purple',me.res('flagpurple'),'wtmail-icon-flagpurple-xs'],
								['black',me.res('flagblack'),'wtmail-icon-flagblack-xs'],
								['gray',me.res('flaggray'),'wtmail-icon-flaggray-xs'],
								['white',me.res('flagwhite'),'wtmail-icon-flagwhite-xs'],
								['brown',me.res('flagbrown'),'wtmail-icon-flagbrown-xs'],
								['azure',me.res('flagazure'),'wtmail-icon-flagazure-xs'],
								['pink',me.res('flagpink'),'wtmail-icon-flagpink-xs'],
								['complete',me.res('flagcomplete'),'wtmail-icon-flagcomplete-xs']
							]
						}),
						//value: this.method,
						valueField: 'id'
					},
					'any': null
				}
			})
		);
		me.messageGrid=Ext.create('Sonicle.webtop.mail.MessageGrid',{
			useNormalStore: true,
			region:'center',
			multifolder: true,
			reloadAction: 'PollAdvancedSearch',
			localSort: true,
			loadMask: null,
			pageSize: 50,
			mys: me.mys
		});
		me.add(me.messageGrid);
		
		if (me.gridMenu) {
			me.messageGrid.on("itemcontextmenu",function(s, rec, itm, i, e) {
				WT.showContextMenu(e, me.gridMenu, { rec: rec, row: i, grid: me.messageGrid });
			});
		}
		
	},
	
	onBeforeClose: function() {
        var me=this,rv=false;
		
        if (me.searchRunning) {
			WT.confirm(
				me.res('advsearch-surestopsearch'),
				function(btn) {
                    if (btn=='yes') {
                        me.stopSearch();
                        me.closeView();
                    }
                },{ 
					title: me.res('advsearch-stopsearch')
				}
			);
        } else {
            rv=true;
        }
        return rv;
	},
	

    runstopSearch: function() {
		var me=this;
		
        if (!this.searchRunning) this.runSearch();
        else {
			WT.confirm(
				me.res('advsearch-surestopsearch'),
				function(btn) {
                    if (btn=='yes') {
                        me.stopSearch();
                    }
                },{ 
					title: me.res('advsearch-stopsearch')
				}
			);
        }
    },

    runSearch: function() {
		var me=this;
		
        if (!me.validateEntries()) {
            WT.warn(WT.res("advsearch-emptyvalue"), { title: me.res("advsearch-invalidsearch") });
            return;
        }
        
        me.searchButton.setText(WT.res('word.stop'));
        me.searchToolbar.disable();
        me.searchGrid.disable();
        var sentries=me.searchGrid.getEntries();
        var entries=new Array();
        Ext.each(sentries,function(e,ix,len) {
            var v=e.getEntryValue();
            var s=e.getEntryField()+"|"+e.getEntryMethod()+"|";
            if (Ext.isDate(v)) {
                var yyyy=""+v.getFullYear();
                var mm=v.getMonth()+1;
                var dd=v.getDate();
                if (mm<10) mm="0"+mm;
                if (dd<10) dd="0"+dd;
                s+=yyyy+mm+dd;
            }
            else s+=v;
            entries[ix]=s;
        });

		WT.ajaxReq(me.mys.ID, 'RunAdvancedSearch', {
			params: {
                folder: me.searchCombo.getValue(),
                subfolders: me.searchCheckbox.getValue(),
                trashspam: me.searchTrashSpam.getValue(),
                andor: me.searchGrid.getAndOr(),
                entries: entries
			},
			callback: function(success,json) {
				if (json.result) {
					me.searchRunning=true;
					me.searchLabel.show();
					me.searchProgress.setText("0%");
					me.searchProgress.show();
					me.messageGrid.getStore().reload({
						callback: me.gridLoaded, 
						scope: me, 
						params: { start: 0 }, 
						addRecords: false 
					});
				} else {
					me.stopSearch();
					WT.error(json.text);
				}
			}
		});					
    },
	
    stopSearch: function() {
		var me=this;
		
        if (me.searchRunning) {
            me.searchRunning=false;
			WT.ajaxReq(me.mys.ID, 'CancelAdvancedSearch', {
				params: { },
				callback: function(success,json) {
					if (json.result) {
					} else {
						WT.error(json.text);
					}
				}
			});
        }
        me.searchButton.setText(WT.res('word.search'));
        me.searchLabel.hide();
        me.searchProgress.hide();
        me.searchToolbar.enable();
        me.searchGrid.enable();
    },
	
    doPolling: function() {
        var me=this,
			s=me.messageGrid.getStore(),
			n=s.getCount();
	
        s.reload({ callback: me.gridLoaded, scope: me, params: { start: n }, addRecords: true });
    },

    gridLoaded: function() {
        var me=this,
			s=me.messageGrid.getStore();
			jsd=s.proxy.reader.rawData;
        if (jsd.finished) {
            me.searchRunning=false;
            me.stopSearch();
            if (jsd.max) {
                WT.warn(me.res("advsearch-maxreached"));
            }
        }
        else if (me.searchRunning) {
            if (jsd.progress) me.searchProgress.setText(jsd.progress+"% "+jsd.curfoldername);
            if (!me.advstask) me.advstask=new Ext.util.DelayedTask();
            me.advstask.delay(5000, me.doPolling, me);
        }
    },
	
    validateEntries: function() {
        var me=this,
			ret=true;
	
        Ext.each(me.searchGrid.getEntries(),function(e) {
           if (!e.isEntryValid()) {
               return ret=false;
           }
        });
        return ret;
    },	

	res: function(key) {
		return this.mys.res(key);
	}
		
});
