/**
 * Created with IntelliJ IDEA.
 * User: riza
 * Date: 09-04-13
 * Time: 12:38
 * To change this template use File | Settings | File Templates.
 */

// Array data for the grids

Ext.grid.dummyData = [
	['Chr1:7282393-152722828',"1p36.33-p36.13",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:31432123-23232322',"1p36.33-p36.13",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:1232323-123344552',"p36.13",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:2323123-232312222',"1p36.31",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:55123123-62342342',"1p36.33-p36.13",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:7234324-234324343',"1p36.33-p36.13",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:23423443-26534343',"p36.13",0.02,0.03, 'GAIN vs NO GAIN'],
	['Chr1:62342342-23423444',"p36.13",0.02,0.03, 'GAIN vs NO GAIN'],

	['Chr1:62342342-23423444',"p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:514212-1232323222',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:51231233-22212332',"p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:8234342-343434333',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:623423433-1232323',"p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:24232321-12323232',"p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:12323233-71231232',"p36.13",0.02,0.03, 'LOSS vs NO LOSS'],
	['Chr1:22232323-12333223',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NO LOSS'],

	['Chr1:62342342-23423444',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:24232321-23423444',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:1232323-234232444',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:62342342-12323w23',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:55123123-23423444',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:24232321-23423444',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:55123123-23423444',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:55123123-24232321',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:62342342-55123123',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:24232321-23423444',"1p36.33-p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN'],
	['Chr1:62342342-23423444',"p36.13",0.02,0.03, 'LOSS vs NORMAL vs GAIN']
];

/**
 *
 * This function is called when user selects Survival Analysis aCGH from the Analysis combo box in the Dataset
 * Explorer toolbar
 *
 */
function loadSurvivalAnalysisaCGHView() {
	console.log("about to load input components");
	loadInputComponents();
}

function loadInputComponents() {

	console.log("about to load input components");

	// -----------------
	// define text areas
	// -----------------

	var txtArea1 = new Ext.form.TextArea({
		hideLabel: true,
		name: 'msg1',
		height: 75
	});

	var txtArea2 = new Ext.form.TextArea({
		hideLabel: true,
		name: 'msg2',
		height: 75
	});

	var txtArea3 = new Ext.form.TextArea({
		hideLabel: true,
		name: 'msg3',
		height: 75
	});

	var alterationTypes = new Ext.form.CheckboxGroup({
		fieldLabel: 'Single Column',
		// Put all controls in a single column with width 75%
		columns: 1,
		height: 75,
		style: 'margin-left: 5px;',
		items: [
			{boxLabel: 'GAIN vs NO GAIN', name: 'cb-col-1'},
			{boxLabel: 'LOSS vs NO LOSS', name: 'cb-col-2', checked: true},
			{boxLabel: 'LOSS vs NORMAL vs GAIN', name: 'cb-col-3'}
		]
	});

	// tool bar for survival analysis acgh input
	var inputToolBar = new Ext.Toolbar({
		height: 30,
		items: ['->',{  // '->' making it right aligned
			xtype: 'button',
			text: 'Run Analysis',
			scale: 'medium',
			iconCls: 'runbutton',
			handler: function () {
				loadGrid();
			}
		}]
	})


	// ------------------
	// define input panel
	// ------------------

	var p = new Ext.Panel({
		layout:'column',
		renderTo: 'analysisContainer',
		bbar: inputToolBar, // bbar
		items: [{
			title: 'Step 1 - Regions*',
			id: 'saRegions',
			tools: [{
				id: 'refresh',
				handler: function(e, toolEl, panel, tc){
					alert('clear me in text box 1...');
				}
			}],
			columnWidth: .25,
			overCls: 'testCls',
			layout: 'fit',
			items: txtArea1
		},{
			title: 'Step 2 - Survival Time*',
			id: 'saSurvivalTime',
			tools: [{
				id: 'refresh',
				handler: function(e, toolEl, panel, tc){
					alert('clear me text box 2...');
				}
			}],
			columnWidth: .25,
			layout: 'fit',
			items: txtArea2
		},{
			title: 'Step 3 - Censoring Variable',
			id: 'saCensoring',
			tools: [{
				id: 'refresh',
				handler: function(e, toolEl, panel, tc){
					alert('clear me text box 3...');
				}
			}],
			columnWidth: .25,
			layout: 'fit',
			items: txtArea3
		},{
			title: 'Step 4 - Alteration Type*',
			id: 'saAlteration',
			columnWidth: .25,
			layout: 'fit',
			items: alterationTypes
		}]
	});

	// ------------------
	// define tooltips
	// ------------------

	var saRegionsTip = new Ext.ToolTip({
		target: 'saRegions',
		html: 'Select time variable from the Data Set Explorer Tree and drag it into the box. For example, ' +
			'"Survival Time". This variable is required.'
	});


}


// TBD:  load grid when user run the analysis


function loadGrid() {

	// ------------------
	// define Grid
	// ------------------

	// tool bar for survival analysis acgh intermediate result
	var iRestToolBar = new Ext.Toolbar({
		height: 30,
		items: ['->', {
			xtype: 'button',
			text: 'Show Survival Plot',
			scale: 'medium',
			iconCls: 'chartcurvebutton'
		}]
	})

	var xg = Ext.grid;

	// shared reader
	var reader = new Ext.data.ArrayReader({}, [
		{name: 'region'},
		{name: 'cytoband'},
		{name: 'p-value', type: 'float'},
		{name: 'fdr', type: 'float'},
		{name: 'alteration'}
	]);

	// grid panel
	// ==========
	var grid = new xg.GridPanel({
		store: new Ext.data.GroupingStore({
			reader: reader,
			data: xg.dummyData,
			sortInfo:{field: 'region', direction: "ASC"},
			groupField:'alteration'
		}),

		columns: [
			{id:'region',header: "Region", width: 60, sortable: true, dataIndex: 'region'},
			{header: "Cytoband", width: 20, sortable: true, dataIndex: 'cytoband'},
			{header: "p-value", width: 20, sortable: true, dataIndex: 'p-value'},
			{header: "fdr", width: 20, sortable: true, dataIndex: 'fdr'},
			{header: "Alteration", width: 20, sortable: true, dataIndex: 'alteration'}
		],

		view: new Ext.grid.GroupingView({
			forceFit:true,
			groupTextTpl: '{text} ({[values.rs.length]} {[values.rs.length > 1 ? "Items" : "Item"]})'
		}),
		bbar: iRestToolBar,
		frame:true,
		height: 250,
		collapsible: true,
		animCollapse: false,
		title: 'Intermediate Result',
		iconCls: 'gridbutton',
		renderTo: 'analysisOutput'
	});

}



