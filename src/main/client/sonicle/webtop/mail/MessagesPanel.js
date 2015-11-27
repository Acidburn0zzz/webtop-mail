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

Ext.define('Sonicle.webtop.mail.MessagesPanel', {
	extend: 'Ext.panel.Panel',
	requires: [
		'Sonicle.webtop.mail.MessageView',
		'Sonicle.webtop.mail.MessageGrid'
	],
	config: {
		pageSize: 25
	},
	
    layout:'border',
    border: false,
	
	viewRegion: 'east',
	viewWidth: 600,
	viewHeight: 400,
    
    filterTextField: null,
    filterCombo: null,
    groupCombo: null,
    bFilterRow: null,
    folderList: null,
    messageView: null,
    messageViewContainer: null,
    folderPanel: null,
    gridMenu: null,
    saveColumnSizes: false,
	saveColumnVisibility: false,
    savePaneSize: false,
    
    initComponent: function() {
		var me=this;
		
		me.callParent(arguments);

		//TODO: addEvents
		/*
        this.addEvents(
            'gridselectionchange',
            'gridselectiondelete',
            'gridrowdblclick',
            'gridcellclick'
        );*/
            
        me.folderList=Ext.create('Sonicle.webtop.mail.MessageGrid',{
            region:'center',
			pageSize: me.getPageSize(),
			mys: me.mys,
			mp: me
        });
		
        var msgSelModel=me.folderList.getSelectionModel();
        msgSelModel.on('selectionchange',me.selectionChanged,me);
		//TODO: grid events
        //msgSelModel.on('selectiondelete',me.actionDelete,me);
        //me.folderList.on('rowdblclick',me.rowDblClicked,me);
        //me.folderList.on('cellclick',me.cellClicked,me);
        //me.folderList.on('deleting',me.clearMessageView,me);
        //me.folderList.on('moving',me.clearMessageView,me);
        if (me.saveColumnSizes) me.folderList.on('columnresize',me.columnResized,me);
		if (me.saveColumnVisibility) {
			me.folderList.on('columnhide', function(ct,col) { me.columnHiddenChange(ct,col,true); });
			me.folderList.on('columnshow', function(ct,col) { me.columnHiddenChange(ct,col,false); });
		}

        me.messageView=Ext.create('Sonicle.webtop.mail.MessageView',{
			mys: me.mys,
			mp: me
        });
        me.messageView.on('messageviewed',me.messageViewed,me);

		var tb=me.toolbar=Ext.create('Ext.toolbar.Paging',{
            store: me.getGridStore(),
            displayInfo: true,
            displayMsg: me.res("pagemessage"),
            emptyMsg: me.res("nomessages"),
            afterPageText: me.res("afterpagetext"),
            beforePageText: me.res("beforepagetext"),
            firstText: me.res("firsttext"),
            lastText: me.res("lasttext"),
            nextText: me.res("nexttext"),
            prevText: me.res("prevtext"),
            refreshText: me.res("refreshtext")
        });
		tb.remove(tb.getComponent('displayItem'));
		tb.add(Ext.create('Ext.button.Button',{
			itemId: "displayItem",
			tooltip: me.res("changepagesize"),
			handler: me.changePageSize,
			scope: me
		}));
		
		
		//TODO: filter components
		/*
        this.filterTextField=new WT.SuggestTextField({
            lookupService: 'mail',
            lookupContext: 'filtersubject',
            width: 150
        });
        this.filterTextField.on('specialkey',this.filterKeyDown,this);
        this.filterCombo=new Ext.form.ComboBox({
            forceSelection: true,
            mode: 'local',
            displayField: 'desc',
            triggerAction: 'all',
            selectOnFocus: true,
            width: 80,
            editable: false,
            store: new Ext.data.SimpleStore({
                fields: ['id','desc'],
                data: [
                    ['subject',this.res('subject')],
                    ['from',this.res('sender')],
                    ['body',this.res('message')],
                    ['sentdate',this.res('sentdate')],
                    ['recvdate',this.res('recvdate')],
                    ['to',this.res('to')],
                    ['cc',this.res('cc')],
                    ['bcc',this.res('bcc')]
                ]
            }),
            value: 'subject',
            valueField: 'id'
        });
        this.filterCombo.on('change',function(cb,nv,ov) {
            this.filterTextField.setLookupContext("filter"+nv);
        },this);
        var txt=this.res('action-filterrow');
        var action=new Ext.Action({text: '', handler: this.actionFilterRow, scope: this, iconCls: 'icon-mail-action-filterrow', tooltip: txt, enableToggle: true});
        
        this.bFilterRow=new Ext.Button(action);

        this.groupCombo=new Ext.form.ComboBox({
            forceSelection: true,
            mode: 'local',
            displayField: 'desc',
            triggerAction: 'all',
            selectOnFocus: true,
            width: 60,
            editable: false,
            store: new Ext.data.SimpleStore({
                fields: ['id','desc'],
                data: [
                    ['none',this.res('group-none')],
                    ['gdate',this.res('column-date')],
                    ['from',this.res('sender')],
                    ['to',this.res('to')]
                ]
            }),
            value: '',
            valueField: 'id'
        });
        this.groupCombo.on('select',function(cb,r,ix) {
            this.groupChanged(r.get("id"));
        },this);
        this.folderList.store.on("metachange",function(s,meta) {
            var gg=meta.groupField;
            this.groupCombo.setValue(gg);
        },this);
        
        this.toolbar.insertButton(0,[
                this.bFilterRow,
                "-",
                this.filterCombo,
                this.filterTextField,
                "-",
                this.res("groupby")+":",
                this.groupCombo
            ]
        );
		 */

        me.messageViewContainer=Ext.create('WT.ux.Panel',{
            region: me.viewRegion,
            cls: 'wtmail-mv-container',
            layout: 'fit',
            split: true,
            collapseMode: 'mini',
            collapsible : false,
            height: me.viewHeight,
			width: me.viewWidth,
            bodyBorder: true,
            border: true,
			tbar: Ext.create('Ext.toolbar.Toolbar',{
				hidden: true,
				items: [
					me.getAction("delete"),
					'-',
					me.getAction("reply"),
					me.getAction("replyall"),
					me.getAction("forward"),
					'-',
					me.getAction("markseen"),
					me.getAction("markunseen")
				]
			}),
            items: [ me.messageView ]
        });
        me.add(me.folderList);
        me.add(me.messageViewContainer);
		me.messageViewContainer.on('resize', function(c,w,h) {
			if (!me.movingPanel) {
				if (me.viewRegion==='east') me.viewWidth=w;
				else me.viewHeight=h;
				me.saveMessageView();
			}
		});
		
		//TODO: save pane size
		/*
        if (this.savePaneSize) {
            this.on("afterlayout",function() {
                var r = this.getLayout()["south"];
                r.split.un("beforeapply", r.onSplitMove, r);            
                r.onSplitMove = r.onSplitMove.createSequence(this.messageViewResized, this);
                r.split.on("beforeapply", r.onSplitMove, r);            
            },this);
        }
		*/


    },
	
	changePageSize: function() {
		var me=this;
		WT.prompt(me.res("changepagesizetext"),{
			title: me.res("changepagesizetitle"),
			fn: function(btn,text) {
				if (btn=='ok') {
					var n=parseInt(text);
					if (isNaN(n)) {
						WT.error(me.res("changepagesizenan"));
					} else {
						me.setPageSize(n);
						me.toolbar.pageSize=n;
						me.folderList.store.reload({
						  params: {start:0,limit:n}
						});
						WT.ajaxReq(me.mys.ID, 'SavePageRows', {
							params: {
								pagerows: n
							}
						});					
					}
				}
			},
			scope: me,
			value: me.getPageSize()
		});
	},
	
	getAction: function(name) {
		return this.mys.getAction(name);
	},
	
	getViewRegion: function() {
		return this.viewRegion;
	},
	
	moveViewPanel: function() {
		var me=this,
			mv=me.messageView,
			mvc=me.messageViewContainer,
			idm=mv.idmessage,
			idf=mv.folder;
		console.log('moveViewPanel');
		me.movingPanel=true;
		me.clearMessageView();
		me.viewRegion=(me.viewRegion==='east'?'south':'east');
		mvc.setRegion(me.viewRegion);
		mvc.setWidth(me.viewWidth);
		mvc.setHeight(me.viewHeight);
		me.updateLayout();
		me.saveMessageView();
		me.showMessage(idf,idm);
		me.movingPanel=false;
	},
	
	saveMessageView: function(sb,newsize) {
		var me=this;
		if (newsize) {
			if (me.viewRegion==="south") me.viewHeight=newsize;
			else me.viewWidth=newsize;
		}
		WT.ajaxReq(me.mys.ID, 'SetMessageView', {
			params: {
				region: me.viewRegion,
				width: me.viewWidth,
				height: me.viewHeight
			}
		});
		
	},
	
/*    
    setViewSize: function(hperc) {
        if (this.ownerCt) {
            var h=parseInt(this.ownerCt.getSize().height*hperc/100);
            this.messageViewContainer.setHeight(h);
            this.doLayout();
        }
    },
    
    printMessageView: function() {
        this.messageView.print();
    },
    
    setGridContextMenu: function(menu) {
        this.gridMenu=menu;
        WT.app.setComponentContextMenu(this.folderList,menu,this.gridContextMenu,this);
    },
    
    gridContextMenu: function(e,t) {
        var grid=this.folderList;
        WT.MessageGrid.setContextGrid(grid);
        var row = grid.view.findRowIndex(t);
        var sm=grid.getSelectionModel();
        if (row>=0 && !sm.isSelected(row)) sm.selectRow(row, false);

    },
    
    groupChanged: function(nv) {
        WT.JsonAjaxRequest({
            url: "ServiceRequest",
            params: {
                service: 'mail',
                action: 'GroupChanged',
                group: nv,
                folder: this.currentFolder
            },
            method: "POST",
            callback: function(o,options) {
                this.reloadGrid();
            },
            scope: this
        });
    },
    
    archiveSelection: function(from,to,selection) {
        this.folderList.archiveSelection(from,to,selection);
    },
    
    archiveSelectionWt: function(from,to,selection,context,customer_id) {
        this.folderList.archiveSelectionWt(from,to,selection,context,customer_id);
    },
    */
   
    //TODO verify rawData and getById
    messageViewed: function(idmessage,millis) {
		var me=this,
			s=me.folderList.store,
			ix=s.findExact('idmessage',idmessage);
        
		if (ix>=0) {
			var r=s.getAt(ix);
			if (r.get("unread")) {
				r.set("unread",false);
				r.set("status","read");
				//TODO: not needed with websocket?
				//var o=s.getProxy().getReader().rawData;
				//o.millis=millis;
				//o.unread--;
				//me.mys.updateUnreads(me.currentFolder,o,false);
			}
		}
    },
   
	/*
    actionFilterRow: function() {
        if (this.bFilterRow.pressed) this.folderList.showFilterRow();
        else this.folderList.hideFilterRow();
    },

    
    
    filterKeyDown: function(tf,e) {
      if (e.getKey()==Ext.EventObject.ENTER) {
        this.depressFilterRowButton();
        var pattern=tf.getValue();
        var field=this.filterCombo.getValue();
        this.folderList.store.baseParams={service: 'mail', action: 'ListMessages', folder: this.currentFolder, searchfield: field, pattern: pattern, refresh:1};
        this.folderList.store.reload({
          params: {start:0,limit:this.ms.pageRows}
        });
        this.folderList.store.baseParams.refresh=0;
      }
    },
    
    
    depressFilterRowButton: function() {
        if (this.bFilterRow.pressed) {
            this.bFilterRow.toggle();
            this.folderList.hideFilterRow();
        }
    },
    
    clearGridSelections: function() {
        this.folderList.getSelectionModel().clearSelections(true);
    },*/
    
    reloadFolder: function(folderid,config) {
        this.currentFolder=folderid;
        this.folderList.reloadFolder(folderid,config);
    },

	//TODO no more ctrlshift?
    selectionChanged: function(sm,r,eopts) {
        var me=this,
			c=sm.getCount();
        //if (c==1&&!ctrlshift) {
		if (c===1) {
            var id=r[0].get('idmessage');
			var fldr=r[0].get('fromfolder');
			if (!fldr) fldr=this.currentFolder;
            if (id!==me.messageView.idmessage)
                me.showMessage(fldr,id);
        } else {
            me.clearMessageView();
        }
        me.fireEvent('gridselectionchanged',sm/*,ctrlshift*/);
    },
    
	showMessage: function(folder,id) {
		if (folder && id) {
			var me=this,
				tbar=me.messageViewContainer.getTopBar();
			tbar.show();
			//tbar.setHeight(24);
			me.messageView._showMessage(folder,id);
		}
	},

    clearMessageView: function() {
		var me=this,
			tbar=me.messageViewContainer.getTopBar();
		tbar.hide();
		//tbar.setHeight(0);
        me.messageView._clear();
    },
/*    
    actionDelete: function() {
        this.folderList.actionDelete();
    },
    
    reloadGrid: function() {
        this.folderList.store.reload();
    },
    
    rowDblClicked: function(grid, rowIndex, e) {
        this.fireEvent('gridrowdblclick',grid,rowIndex,e);
    },
    
    cellClicked: function(grid, rowIndex, colIndex, e) {
        this.fireEvent('gridcellclick',grid,rowIndex,colIndex, e);
    },
    */
   
    columnResized: function(ct, col, w) {
		WT.ajaxReq(this.mys.ID, 'SaveColumnSize', {
			params: {
                name: col.dataIndex,
                size: w
			}
		});					
        
    },
	
	columnHiddenChange: function(ct,col,h) {
		WT.ajaxReq(this.mys.ID, 'SaveColumnVisibility', {
			params: {
				folder: this.currentFolder,
				name: col.dataIndex,
				visible: !h
			},
		});
	},

	/*
    messageViewResized: function() {
        var ah=this.messageViewContainer.getHeight();
        if (ah==this.lastHView) return;
        this.lastHView=ah;
        var hperc=(ah*100/this.getHeight());
        WT.JsonAjaxRequest({
            url: "ServiceRequest",
            params: {
                service: 'mail',
                action: 'SavePaneSize',
                hperc: hperc
            },
            method: "POST",
            callback: function(o,options) {

            },
            scope: this
        });
        
    },
    
	*/
   
    res: function(name) {
		return this.mys.res(name);
	},
	
    getGridStore: function() {
		return this.folderList.store;
	}
   
});