package org.eclipse.wst.validation.ui.internal.preferences;

/*******************************************************************************
 * Copyright (c) 2001, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.MenuAdapter;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.dialogs.PropertyPage;
import org.eclipse.wst.validation.Validator;
import org.eclipse.wst.validation.internal.ConfigurationManager;
import org.eclipse.wst.validation.internal.GlobalConfiguration;
import org.eclipse.wst.validation.internal.ProjectConfiguration;
import org.eclipse.wst.validation.internal.ValManager;
import org.eclipse.wst.validation.internal.ValPrefManagerProject;
import org.eclipse.wst.validation.internal.ValidatorMetaData;
import org.eclipse.wst.validation.internal.model.ProjectPreferences;
import org.eclipse.wst.validation.internal.operations.ValidatorManager;
import org.eclipse.wst.validation.internal.plugin.ValidationPlugin;
import org.eclipse.wst.validation.internal.ui.ContextIds;
import org.eclipse.wst.validation.internal.ui.DelegatingValidatorPreferencesDialog;
import org.eclipse.wst.validation.internal.ui.plugin.ValidationUIPlugin;
import org.eclipse.wst.validation.ui.internal.ImageNames;
import org.eclipse.wst.validation.ui.internal.ValUIMessages;
import org.eclipse.wst.validation.ui.internal.dialog.FilterDialog;

/**
 * From this page the user can configure individual validators on individual projects.
 * 
 * @author karasiuk
 */
public class ValidationPropertyPage extends PropertyPage  {

	private IValidationPage _pageImpl;
	private Shell 			_shell;

	public interface IValidationPage {
		Composite createPage(Composite parent) throws InvocationTargetException;

		boolean performOk() throws InvocationTargetException;

		boolean performDefaults() throws InvocationTargetException;

		Composite getControl();

		void dispose();

		void loseFocus();

		void gainFocus();
	}

	public class InvalidPage implements IValidationPage {
		private Composite page = null;

		private Composite composite = null;
		private GridLayout layout = null;
		private Label messageLabel = null;

		public InvalidPage(Composite parent) {
			page = createPage(parent);
		}

		/**
		 * This page is added to the Properties guide if some internal problem
		 * occurred; for example, the highlighted item in the workbench is not
		 * an IProject (according to this page's plugin.xml, this page is only
		 * valid when an IProject is selected).
		 */
		public Composite createPage(Composite parent) {
			noDefaultAndApplyButton();

			final ScrolledComposite sc1 = new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
			sc1.setLayoutData(new GridData(GridData.FILL_BOTH));
			composite = new Composite(sc1, SWT.NONE);
			sc1.setContent(composite);
			layout = new GridLayout();
			composite.setLayout(layout);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, ContextIds.VALIDATION_PROPERTIES_PAGE);

			messageLabel = new Label(composite, SWT.NONE);
			messageLabel.setText(ValUIMessages.VBF_EXC_INVALID_REGISTER);

			composite.setSize(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));

			return composite;
		}

		public boolean performDefaults() {
			return true;
		}

		/**
		 * Since this page occurs under invalid circumstances, there is nothing
		 * to save.
		 */
		public boolean performOk() {
			return true;
		}

		public Composite getControl() {
			return page;
		}

		public void dispose() {
			messageLabel.dispose();
			// layout.dispose();
			composite.dispose();
		}

		public void loseFocus() {
		}

		public void gainFocus() {
		}
	}

	private class ValidatorListPage implements IValidationPage {
		private Composite 		_page;

		private Composite 		_composite;
		private TableViewer 	_validatorList;
		private Button 			_enableAllButton;
		private Button 			_disableAllButton;
		private Button 			_override;
		private Button 			_suspend;
		private Link			_configLink;
		private Button			_addValidationBuilder;
		private Table 			_validatorsTable;
		private ProjectPreferences	_projectPreferences;


		/**
		 * This class is provided for the CheckboxTableViewer in the
		 * ValidationPropertiesPage$ValidatorListPage class.
		 */
		public class ValidationContentProvider implements IStructuredContentProvider {
			public void dispose() {
			}

			public Object[] getElements(Object inputElement) {
				if (inputElement instanceof ValidatorWrapper[]) {
					return (ValidatorWrapper[]) inputElement;
				}
				return new Object[0];
			}

			public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			}
		}

		/**
		 * This class is provided for
		 * ValidationPropertiesPage$ValidatorListPage's checkboxTableViewer
		 * element.
		 */
		public class ValidationLabelProvider extends LabelProvider implements ITableLabelProvider {
			public String getText(Object element) {
				if (element == null)return ""; //$NON-NLS-1$
				else if (element instanceof ValidatorWrapper)
					return ((ValidatorWrapper) element).getName();
				else
					return super.getText(element);
			}

			private Image getImage(String imageName) {
				boolean isDisabled = !_validatorsTable.isEnabled();
				if (isDisabled)
					imageName = imageName + ImageNames.disabled;

				return ValidationUIPlugin.getPlugin().getImage(imageName);
			}

			public Image getColumnImage(Object element, int columnIndex) {
				ValidatorWrapper v = (ValidatorWrapper) element;
				if (columnIndex == 1) {
					return getImage(v.isManual() ? ImageNames.okTable
							: ImageNames.failTable);
				} else if (columnIndex == 2) {
					return getImage(v.isBuild() ? ImageNames.okTable
							: ImageNames.failTable);
				} else if (columnIndex == 3) {
					if (v.isV2())return getImage(ImageNames.settings);
					if (v.getValidator().getDelegatingId() != null)return getImage(ImageNames.settings);
					return  null;
				}
				return null;
			}

			public String getColumnText(Object element, int columnIndex) {
				if (columnIndex == 0)return ((ValidatorWrapper) element).getName();
				return null;
			}
		}

		public ValidatorListPage(Composite parent) throws InvocationTargetException {
			_page = createPage(parent);
		}

		private void setupTableColumns(Table table) {
			TableColumn validatorColumn = new TableColumn(table, SWT.NONE);
			validatorColumn.setText(ValUIMessages.VALIDATOR);
			validatorColumn.setResizable(false);
			validatorColumn.setWidth(320);
			TableColumn manualColumn = new TableColumn(table, SWT.NONE);
			manualColumn.setText(ValUIMessages.MANUAL);
			manualColumn.setResizable(false);
			manualColumn.setWidth(40);
			TableColumn buildColumn = new TableColumn(table, SWT.NONE);
			buildColumn.setText(ValUIMessages.BUILD);
			buildColumn.setResizable(false);
			buildColumn.setWidth(40);
			TableColumn settingsColumn = new TableColumn(table, SWT.NONE);
			settingsColumn.setText(ValUIMessages.SETTINGS);
			settingsColumn.setResizable(false);
			settingsColumn.setWidth(50);
		}

		public Composite createPage(Composite parent) throws InvocationTargetException {
			final ScrolledComposite sc1 = new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
			sc1.setLayoutData(new GridData(GridData.FILL_BOTH));
			_composite = new Composite(sc1, SWT.NONE);
			sc1.setContent(_composite);
			_composite.setLayout(new GridLayout());
			 PlatformUI.getWorkbench().getHelpSystem().setHelp(_composite, ContextIds.VALIDATION_PREFERENCE_PAGE);

			Composite validatorGroup = new Composite(_composite, SWT.NONE);

			GridLayout validatorGroupLayout = new GridLayout();
			validatorGroupLayout.numColumns = 2;
			validatorGroup.setLayout(validatorGroupLayout);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(validatorGroup, ContextIds.VALIDATION_PREFERENCE_PAGE);

			addOverride(validatorGroup);
			addConfigLink(validatorGroup);
			addSuspend(validatorGroup);
			addValidationBuilder(validatorGroup);
			new Label(validatorGroup, SWT.NONE).setLayoutData(new GridData());

			Label listLabel = new Label(validatorGroup, SWT.NONE);
			GridData listLabelData = new GridData(GridData.FILL_HORIZONTAL);
			listLabelData.horizontalSpan = 2;
			listLabel.setLayoutData(listLabelData);
			listLabel.setText(ValUIMessages.PREF_VALLIST_TITLE);
			
//			ScrolledComposite sc = new ScrolledComposite(validatorGroup, SWT.V_SCROLL);
//			GridData gd = new GridData(GridData.FILL_BOTH);
//			gd.horizontalSpan = 2;
//			sc.setLayoutData(gd);
//			sc.setLayout(new GridLayout());
//			Composite table = new Composite(sc, SWT.NONE);
//			sc.setContent(table);
//			table.setLayout(new GridLayout());

			_validatorsTable = new Table(validatorGroup, SWT.BORDER | SWT.FULL_SELECTION);
//			sc.setContent(_validatorsTable);
			_validatorsTable.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			TableLayout tableLayout = new TableLayout();
			tableLayout.addColumnData(new ColumnWeightData(160, true));
			tableLayout.addColumnData(new ColumnWeightData(40, true));
			tableLayout.addColumnData(new ColumnWeightData(30, true));
			tableLayout.addColumnData(new ColumnWeightData(40, true));

			_validatorsTable.setHeaderVisible(true);
			_validatorsTable.setLinesVisible(true);
			_validatorsTable.setLayout(tableLayout);

			_validatorList = new TableViewer(_validatorsTable);
			GridData validatorListData = new GridData(GridData.FILL_HORIZONTAL);
			validatorListData.horizontalSpan = 2;
			_validatorsTable.setLayoutData(validatorListData);
			_validatorList.getTable().setLayoutData(validatorListData);
			_validatorList.setLabelProvider(new ValidationLabelProvider());
			_validatorList.setContentProvider(new ValidationContentProvider());
			_validatorList.setSorter(new ViewerSorter());
			setupTableColumns(_validatorsTable);

			_validatorList.setInput(ValidatorWrapper.createWrappers(getProject()));
			_validatorsTable.addMouseListener(new MouseAdapter() {

				public void mouseDown(MouseEvent e) {
					if (e.button != 1)
						return;

					TableItem tableItem = _validatorsTable.getItem(new Point(
							e.x, e.y));
					if (tableItem == null || tableItem.isDisposed()) {
						// item no longer exists
						return;
					}
					int columnNumber;
					int columnsCount = _validatorsTable.getColumnCount();
					if (columnsCount == 0) {
						// If no TableColumn, Table acts as if it has a single column
						// which takes the whole width.
						columnNumber = 0;
					} else {
						columnNumber = -1;
						for (int i = 0; i < columnsCount; i++) {
							Rectangle bounds = tableItem.getBounds(i);
							if (bounds.contains(e.x, e.y)) {
								columnNumber = i;
								break;
							}
						}
						if (columnNumber == -1) {
							return;
						}
					}

					columnClicked(columnNumber);
				}
			});

			_validatorsTable.setMenu(createContextMenu());
			_validatorsTable.addFocusListener(new FocusAdapter() {

				public void focusGained(FocusEvent e) {
					super.focusGained(e);
					if (_validatorsTable.getSelectionCount() == 0) {
						_validatorsTable.select(0);
					}
				}
			});
			
			addButtons(validatorGroup);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(_disableAllButton, ContextIds.VALIDATION_PREFERENCE_PAGE);

			// Have to set the tab order or only the first checkbox in a
			// Composite can be tabbed to. (Seems to apply only to checkboxes. Have to use the
			// arrow key to navigate the checkboxes.)
			validatorGroup.setTabList(new Control[] { _override, _suspend,
				});

			updateWidgets();

			applyDialogFont(_composite);
			_composite.setSize(_composite.computeSize(SWT.DEFAULT, SWT.DEFAULT));

			return _composite;
		}

		private void addButtons(Composite validatorGroup) {
			
			Composite buttons = new Composite(validatorGroup, SWT.NONE);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = 2;
			buttons.setLayout(new RowLayout());
			
			_enableAllButton = new Button(buttons, SWT.PUSH);
			_enableAllButton.setText(ValUIMessages.PREF_BUTTON_ENABLEALL);
			_enableAllButton.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					try {
						performEnableAll();
					} catch (InvocationTargetException exc) {
						displayAndLogError(ValUIMessages.VBF_EXC_INTERNAL_TITLE,ValUIMessages.VBF_EXC_INTERNAL_PAGE, exc);
					}
				}
			});

			_disableAllButton = new Button(buttons, SWT.PUSH);
			_disableAllButton.setText(ValUIMessages.PREF_BUTTON_DISABLEALL);
			_disableAllButton.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					try {
						performDisableAll();
					} catch (InvocationTargetException exc) {
						displayAndLogError(ValUIMessages.VBF_EXC_INTERNAL_TITLE, ValUIMessages.VBF_EXC_INTERNAL_PAGE, exc);
					}
				}
			});
		}

		private void addSuspend(Composite validatorGroup) {
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = 2;
			_suspend = new Button(validatorGroup, SWT.CHECK);
			_suspend.setLayoutData(gd);
			_suspend.setText(ValUIMessages.DISABLE_VALIDATION);
			_suspend.setSelection(getProjectPreferences().getSuspend());
			_suspend.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					_suspend.setFocus();
					enableDisableWidgets();
					_validatorList.refresh();
				}
			});
		}
		
		private void addConfigLink(Composite validatorGroup){
			_configLink = new Link(validatorGroup,SWT.None);
			GridData layout = new GridData(GridData.HORIZONTAL_ALIGN_END);
			_configLink.setLayoutData(layout);
			_configLink.setText("<A>"+ //$NON-NLS-1$
				ValUIMessages.ConfigWsSettings+"</A>"); //$NON-NLS-1$
			_configLink.addSelectionListener(new SelectionListener() {
				public static final String DATA_NO_LINK = "PropertyAndPreferencePage.nolink"; //$NON-NLS-1$

				public void doLinkActivated(Link e) {
					String id = getPreferencePageID();
					PreferencesUtil.createPreferenceDialogOn(getShell(), id, new String[]{id}, DATA_NO_LINK).open();
					try {
						updateWidgets();
					} catch (InvocationTargetException ie) {

					}
				}

				private String getPreferencePageID() {
					return "ValidationPreferencePage"; //$NON-NLS-1$
				}

				public void widgetDefaultSelected(SelectionEvent e) {
					doLinkActivated((Link) e.widget);					
				}

				public void widgetSelected(SelectionEvent e) {
					doLinkActivated((Link) e.widget);					
				}
			});
			
		}
		
		/**
		 * If the current project doesn't have the validation builder configured on it, 
		 * and the user has asked us to add a builder, add the builder. 
		 * Otherwise return without doing anything.
		 */
		private void addBuilder() {
			if (_addValidationBuilder != null && _addValidationBuilder.getSelection())
				ValidatorManager.addProjectBuildValidationSupport(getProject());
		}

		
		private void addValidationBuilder(Composite validatorGroup) {
			if (hasValidationBuilder())return;
			
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = 2;
			_addValidationBuilder = new Button(validatorGroup, SWT.CHECK);
			_addValidationBuilder.setLayoutData(gd);
			_addValidationBuilder.setText(ValUIMessages.ADD_VALIDATION_BUILDER);
			_addValidationBuilder.setSelection(false);
		}
		
		/**
		 * Answer if this project has a validator builder assigned to it.
		 */
		private boolean hasValidationBuilder(){
			try {
				IProjectDescription description = getProject().getDescription();
				ICommand[] commands = description.getBuildSpec();
				for (int i = 0; i < commands.length; i++) {
					if (commands[i].getBuilderName().equals(ValidationPlugin.VALIDATION_BUILDER_ID))
						return true;
				}
				return false;
			}
			catch (CoreException e){
				ValidationPlugin.getPlugin().handleException(e);
			}
			return false;
		}


		private void addOverride(Composite validatorGroup) {
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
//			gd.horizontalSpan = 2;
			_override = new Button(validatorGroup, SWT.CHECK);
			_override.setLayoutData(gd);
			_override.setText(ValUIMessages.LabelEnableProjectSpecific);
			_override.setSelection(getProjectPreferences().getOverride());
			_override.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					_override.setFocus();
					if (ValManager.getDefault().getGlobalPreferences().getOverride()){
						enableDisableWidgets();
						_validatorList.refresh();
					}
					else {
						MessageDialog.openWarning(_shell, ValUIMessages.Validation, 
							ValUIMessages.ProjectOverridesNotAllowed);						
					}
				}

			});
		}

		protected Menu createContextMenu() {
			final Menu menu = new Menu(_validatorsTable.getShell(), SWT.POP_UP);
			final MenuItem manualItem = new MenuItem(menu, SWT.CHECK);
			manualItem.setText(ValUIMessages.PREF_MNU_MANUAL);
			final MenuItem buildItem = new MenuItem(menu, SWT.CHECK);
			buildItem.setText(ValUIMessages.PREF_MNU_BUILD);
			final MenuItem settingsItem = new MenuItem(menu, SWT.PUSH);
			settingsItem.setText(ValUIMessages.PREF_MNU_SETTINGS);

			class MenuItemListener extends SelectionAdapter {
				public void widgetSelected(SelectionEvent e) {
					MenuItem menuItem = (MenuItem) e.getSource();
					int index = menu.indexOf(menuItem) + 1;
					columnClicked(index);
				}
			}
			MenuItemListener listener = new MenuItemListener();
			manualItem.addSelectionListener(listener);
			buildItem.addSelectionListener(listener);
			settingsItem.addSelectionListener(listener);

			menu.addMenuListener(new MenuAdapter() {
				public void menuShown(MenuEvent e) {
					IStructuredSelection selection = (IStructuredSelection) _validatorList
							.getSelection();
					ValidatorWrapper vw = (ValidatorWrapper) selection
							.getFirstElement();
					manualItem.setSelection(vw.isManual());
					buildItem.setSelection(vw.isBuild());
					// settingsItem.setEnabled(vmd.isDelegating());
				}
			});

			return menu;
		}

		protected void columnClicked(int columnToEdit) {
			IStructuredSelection selection = (IStructuredSelection) _validatorList.getSelection();
			ValidatorWrapper val = (ValidatorWrapper) selection.getFirstElement();

			switch (columnToEdit) {
			case 1:
				val.setManual(!val.isManual());
				break;
			case 2:
				val.setBuild(!val.isBuild());
				break;
			case 3:
				Validator.V2 v2 = val.getValidator().asV2Validator();
				if (v2 != null){
					FilterDialog fd = new FilterDialog(_shell, val.getValidator(), getProject());
					fd.open();
				}
				else {
					handleOldDelegate(val);
				}

				break;
			default:
				break;
			}
			_validatorList.refresh();
		}

		private void handleOldDelegate(ValidatorWrapper val) {
			try {
				Validator.V1 v1 = val.getValidator().asV1Validator();
				if (v1 == null)return;
				
				ValidatorMetaData vmd = v1.getVmd();
			    if (!vmd.isDelegating())return;
			    
			    GlobalConfiguration gc = ConfigurationManager.getManager().getGlobalConfiguration();
			    String delegateID = gc.getDelegateUniqueName(vmd);
			    Shell shell = Display.getCurrent().getActiveShell();
			    DelegatingValidatorPreferencesDialog dialog = 
			    	new DelegatingValidatorPreferencesDialog(shell, vmd, delegateID);
			
			    dialog.setBlockOnOpen(true);
			    dialog.create();
			
			    int result = dialog.open();
		        if (result == Window.OK)gc.setDelegateUniqueName(vmd, dialog.getDelegateID());
			}
			catch (InvocationTargetException e){
				
			}
		}

		protected void updateWidgets() throws InvocationTargetException {
			// Need to update even the widgets that do not change based on another
			// widgets because of performDefaults(). If performDefaults() is selected,
			// then the pagePreferences values are reset, and these widgets
			// might also need to be updated.
			updateAllWidgets();
			updateHelp();
		}

		protected void updateWidgetsForDefaults() throws InvocationTargetException {
			updateAllWidgets();
			updateHelp();
		}

		private void updateAllWidgets() throws InvocationTargetException {
			_suspend.setSelection(getProjectPreferences().getSuspend());
			_override.setSelection(getProjectPreferences().getOverride());
			enableDisableWidgets();
			_validatorList.setInput(ValidatorWrapper.createWrappers(getProject()));
			_validatorList.refresh();
		}

		public boolean performOk() throws InvocationTargetException {
			
			addBuilder();
			// [213631] this warning should only be shown if the user actually tried to override
			// the validators
			if (!ValManager.getDefault().getGlobalPreferences().getOverride() && _override.getSelection()){
				MessageDialog.openWarning(_shell, ValUIMessages.Validation, 
					ValUIMessages.ProjectOverridesNotAllowed);
				return false;
			}
			updateV1ProjectSettings();
			getProjectPreferences().setSuspend(_suspend.getSelection());
			getProjectPreferences().setOverride(_override.getSelection());
			updateValidators();
			IProject project = getProject();
			ValPrefManagerProject vpm = new ValPrefManagerProject(project);
			vpm.savePreferences(getProjectPreferences(), getValidators());
			return true;
		}
		
		/**
		 * Update the version 1 project settings.
		 */
		private void updateV1ProjectSettings() {
			try {
				ProjectConfiguration pc = ConfigurationManager.getManager()
					.getProjectConfiguration(getProject());
				pc.setDoesProjectOverride(_override.getSelection());
				pc.setDisableAllValidation(_suspend.getSelection());
			}
			catch (InvocationTargetException e){
				ValidationPlugin.getPlugin().handleException(e);
			}
			
			
		}

		private Validator[] getValidators(){
			Validator[] vals = new Validator[_validatorsTable.getItems().length];
			int i = 0;
			for (TableItem ti : _validatorsTable.getItems()) {
				ValidatorWrapper vw = (ValidatorWrapper) ti.getData();
				vals[i++] = vw.getValidator();
			}		
			return vals;
		}
				
		/**
		 * Answer the specific project preferences. If the project didn't have any specific project
		 * preferences, then create a default set.
		 * @return
		 */
		private ProjectPreferences getProjectPreferences(){
			if (_projectPreferences == null){
				IProject project = getProject();
				_projectPreferences = ValManager.getDefault().getProjectPreferences(project);
				if (_projectPreferences == null)_projectPreferences = new ProjectPreferences(project);
			}
			return _projectPreferences;
		}


		/**
		 * Update the validates to reflect the preference changes.
		 */
		private void updateValidators() {
			for (TableItem ti : _validatorsTable.getItems()) {
				ValidatorWrapper vw = (ValidatorWrapper) ti.getData();
				vw.updateValidator();
			}		
		}

		public boolean performDefaults() throws InvocationTargetException {
			ValManager.getDefault().restoreDefaults();
			updateWidgetsForDefaults();
			getDefaultsButton().setFocus();
			return true;
		}

		public boolean performEnableAll() throws InvocationTargetException {
			setAllValidators(true);
			_enableAllButton.setFocus();
			_validatorList.refresh();
			return true;
		}

		private void setAllValidators(boolean bool) {
			TableItem[] items = _validatorsTable.getItems();
			for (int i = 0; i < items.length; i++) {
				ValidatorWrapper vw = (ValidatorWrapper) items[i].getData();
				vw.setManual(bool);
				vw.setBuild(bool);
			}
		}

		public boolean performDisableAll() throws InvocationTargetException {
			setAllValidators(false);
			_disableAllButton.setFocus();
			_validatorList.refresh();
			return true;
		}

		protected void updateHelp() {
			PlatformUI.getWorkbench().getHelpSystem().setHelp(_suspend, ContextIds.VALIDATION_PREFERENCE_PAGE_DISABLE_ALL_ENABLED);
		}

		/*
		 * Store the current values of the controls into the preference store.
		 */

		public Composite getControl() {
			return _page;
		}

		public void dispose() {
			_override.dispose();
			_suspend.dispose();
			_configLink.dispose();
			_disableAllButton.dispose();
			_enableAllButton.dispose();
			_validatorList.getTable().dispose();
			_composite.dispose();
		}
		
		/**
		 * Enable or disable the widgets based on some top level preferences. 
		 */
		private void enableDisableWidgets() {
			boolean globalOverride = ValManager.getDefault().getGlobalPreferences().getOverride();
			boolean enable = !_suspend.getSelection() & _override.getSelection() & globalOverride;
			_suspend.setEnabled(_override.getSelection() & globalOverride);
			_validatorsTable.setEnabled(enable);
			_enableAllButton.setEnabled(enable);
			_disableAllButton.setEnabled(enable);
			_configLink.setEnabled(!globalOverride || !_override.getSelection());
		}

		public void loseFocus() {
			// This page does not need to cache anything before it loses focus.
		}

		public void gainFocus() {
		}
	}

	/**
	 * Wrap a validator so that temporary changes can be made to it.
	 * 
	 * @author karasiuk
	 * 
	 */
	private static class ValidatorWrapper {
		private boolean _manual;
		private boolean _build;
		private Validator _validator;

		public ValidatorWrapper(Validator validator) {
			_validator = validator;
			_manual = validator.isManualValidation();
			_build = validator.isBuildValidation();
		}

		/**
		 * Update the validator to reflect the value in the preference.
		 */
		public void updateValidator() {
			_validator.setBuildValidation(_build);
			_validator.setManualValidation(_manual);
		}

		public String getName() {
			return _validator.getName();
		}

		/**
		 * Create wrappers for all the validators.
		 */
		public static ValidatorWrapper[] createWrappers(IProject project) {
			Validator[] validators = ValManager.getDefault().getValidators(project);
			ValidatorWrapper[] wrappers = new ValidatorWrapper[validators.length];
			for (int i = 0; i < validators.length; i++)
				wrappers[i] = new ValidatorWrapper(validators[i]);
			return wrappers;
		}

		public boolean isManual() {
			return _manual;
		}

		/** Is this a version 2 validator? */
		public boolean isV2() {
			return _validator.asV2Validator() != null;
		}

		/**
		 * Refresh the wrapper settings from the under lying validator model.
		 */
		public void refreshWrapper() {
			_build = _validator.isBuildValidation();
			_manual = _validator.isManualValidation();
		}

		public void setManual(boolean manual) {
			_manual = manual;
		}

		public boolean isBuild() {
			return _build;
		}

		public void setBuild(boolean build) {
			_build = build;
		}

		public Validator getValidator() {
			return _validator;
		}
	}

	/*
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {
		try {
			_shell = parent.getShell();
			_pageImpl = new ValidatorListPage(parent);
		} catch (Throwable exc) {
			_pageImpl = new InvalidPage(parent);
			displayAndLogError(ValUIMessages.VBF_EXC_INTERNAL_TITLE, ValUIMessages.VBF_EXC_INTERNAL_PAGE, exc);
		}

		return _pageImpl.getControl();
	}

	/**
	 * Performs special processing when this page's Defaults button has been
	 * pressed.
	 * <p>
	 * This is a framework hook method for subclasses to do special things when
	 * the Defaults button has been pressed. Subclasses may override, but should
	 * call <code>super.performDefaults</code>.
	 * </p>
	 */
	protected void performDefaults() {
		super.performDefaults();

		try {
			_pageImpl.performDefaults();
		} catch (Throwable exc) {
			displayAndLogError(ValUIMessages.VBF_EXC_INTERNAL_TITLE, ValUIMessages.VBF_EXC_INTERNAL_PAGE, exc);
		}
	}

	public boolean performOk() {
		try {
			return _pageImpl.performOk();
		} catch (Throwable exc) {
			displayAndLogError(ValUIMessages.VBF_EXC_INTERNAL_TITLE, ValUIMessages.VBF_EXC_INTERNAL_PAGE, exc);
			return false;
		}
	}

	/**
	 * Since the pages are inner classes of a child PreferencePage, not a
	 * PreferencePage itself, DialogPage's automatic disposal of its children's
	 * widgets cannot be used. Instead, dispose of each inner class' widgets
	 * explicitly.
	 */
	public void dispose() {
		super.dispose();
		try {
			if (_pageImpl != null) {
				_pageImpl.dispose();
				_pageImpl = null;
			}
		} catch (Throwable exc) {
			displayAndLogError(ValUIMessages.VBF_EXC_INTERNAL_TITLE, ValUIMessages.VBF_EXC_INTERNAL_PAGE, exc);
		}
	}

	private void logError(Throwable exc) {
		ValidationUIPlugin.getPlugin().handleException(exc);
	}

	/*
	 * package visibility because if this method is private, then the compiler
	 * needs to create a synthetic accessor method for the internal classes, and
	 * that can have performance implications.
	 */
	void displayAndLogError(String title, String message, Throwable exc) {
		logError(exc);
		displayMessage(title, message, org.eclipse.swt.SWT.ICON_ERROR);
	}

	private void displayMessage(String title, String message, int iIconType) {
		MessageBox messageBox = new MessageBox(getShell(),
			org.eclipse.swt.SWT.OK | iIconType | org.eclipse.swt.SWT.APPLICATION_MODAL);
		messageBox.setMessage(message);
		messageBox.setText(title);
		messageBox.open();
	}

	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
		super.setVisible(visible);

		if (_pageImpl == null)return;
		if (visible)_pageImpl.gainFocus();
		else _pageImpl.loseFocus();
	}

	/**
	 * @see org.eclipse.jface.preference.PreferencePage#getDefaultsButton()
	 */
	protected Button getDefaultsButton() {
		return super.getDefaultsButton();
	}
	
	/**
	 * Returns the selected project.
	 */
	public IProject getProject() {
		IAdaptable selectedElement = getElement();
		if (selectedElement == null)return null;
		if (selectedElement instanceof IProject)return (IProject) selectedElement;

		Object adaptedObject = selectedElement.getAdapter(IProject.class);
		if (adaptedObject instanceof IProject)return (IProject) adaptedObject;
		return null;

	}
}