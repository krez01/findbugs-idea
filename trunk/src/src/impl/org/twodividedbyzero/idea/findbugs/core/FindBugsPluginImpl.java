/*
 * Copyright 2010 Andre Pfeiler
 *
 * This file is part of FindBugs-IDEA.
 *
 * FindBugs-IDEA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FindBugs-IDEA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FindBugs-IDEA.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.twodividedbyzero.idea.findbugs.core;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.psi.PsiFile;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import edu.umd.cs.findbugs.BugCollection;
import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.ba.AnalysisException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.actions.AnalyzeChangelistFiles;
import org.twodividedbyzero.idea.findbugs.actions.AnalyzeCurrentEditorFile;
import org.twodividedbyzero.idea.findbugs.actions.ProjectHolder;
import org.twodividedbyzero.idea.findbugs.common.ExtendedProblemDescriptor;
import org.twodividedbyzero.idea.findbugs.common.FindBugsPluginConstants;
import org.twodividedbyzero.idea.findbugs.common.VersionManager;
import org.twodividedbyzero.idea.findbugs.common.event.EventManagerImpl;
import org.twodividedbyzero.idea.findbugs.common.exception.FindBugsPluginException;
import org.twodividedbyzero.idea.findbugs.common.util.FindBugsUtil;
import org.twodividedbyzero.idea.findbugs.common.util.IdeaUtilImpl;
import org.twodividedbyzero.idea.findbugs.gui.common.BalloonTipFactory;
import org.twodividedbyzero.idea.findbugs.gui.preferences.ConfigurationPanel;
import org.twodividedbyzero.idea.findbugs.gui.toolwindow.view.ToolWindowPanel;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;
import org.twodividedbyzero.idea.findbugs.preferences.PersistencePreferencesBean;
import org.twodividedbyzero.idea.findbugs.resources.GuiResources;
import org.twodividedbyzero.idea.findbugs.resources.ResourcesLoader;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrepdo@dev.java.net>
 * @version $Revision$
 * @since 0.1.1
 */
@State(
		name = FindBugsPluginConstants.PLUGIN_ID,
		storages = {
				@Storage(id = "other", file = "$PROJECT_FILE$"),
				@Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/findbugs-idea.xml", scheme = StorageScheme.DIRECTORY_BASED)})
public class FindBugsPluginImpl implements ProjectComponent, FindBugsPlugin, Configurable, PersistentStateComponent<PersistencePreferencesBean> {

	/*<category name="org.twodividedbyzero.idea.findbugs">
   		<priority value="DEBUG" />
   		<appender-ref ref="CONSOLE-DEBUG"/>
   		<appender-ref ref="FILE"/>
	</category>*/
	private static final Logger LOGGER = Logger.getInstance(FindBugsPluginImpl.class.getName());

	private final Project _project;
	private ToolWindow _toolWindow;
	private final Application _application;
	private static final Set<AnAction> _toolbarActions;
	private static final Set<AnAction> _registeredActions;

	private ConfigurationPanel _configPanel;
	private FindBugsPreferences _preferences;


	static {
		_registeredActions = new HashSet<AnAction>();
		_toolbarActions = new HashSet<AnAction>();
		final AnAction action = new AnalyzeCurrentEditorFile();
		_toolbarActions.add(action);
		action.getTemplatePresentation().setText("Run FindBugs analysis on the current editor file", true);
		action.getTemplatePresentation().setIcon(GuiResources.FINDBUGS_EXECUTE_ICON);
	}


	//@State


	public FindBugsPluginImpl(final Project project) {
		_project = project;
		_application = ApplicationManager.getApplication();

		try {
			if (project != null) {
				LOGGER.info(VersionManager.getFullVersion() + " Plugin loaded with project base dir: " + IdeaUtilImpl.getProjectPath(project));
				LOGGER.info("using Findbugs version " + FindBugsUtil.getFindBugsFullVersion());
			} else {
				LOGGER.info(VersionManager.getFullVersion() + " Plugin loaded with no project.");
			}

		} catch (Throwable t) {
			LOGGER.error("Project initialisation failed.", t);
		}

	}


	public void initComponent() {
		LOGGER.debug("initComponent");
	}


	public void disposeComponent() {
		LOGGER.debug("disposeComponent");
	}


	@NotNull
	public String getComponentName() {
		return FindBugsPluginConstants.PLUGIN_ID;
	}


	public void projectOpened() {
		// called when project is opened
		LOGGER.debug("project is opened");
		initToolWindow();
		setActionGroupsIcon();
		registerToolbarActions();

		ActionManager.getInstance().registerAction(FindBugsPluginConstants.FINDBUGS_PROJECT_HOLDER, new ProjectHolder(_project), PluginId.getId(FindBugsPluginConstants.PLUGIN_ID));
	}


	public void projectClosed() {
		ActionManager.getInstance().unregisterAction("FindBugs.ProjectHolder");
		LOGGER.debug("project is being closed");
		EventManagerImpl.getInstance().removeEventListener(_project);
		final AnalyzeChangelistFiles action = (AnalyzeChangelistFiles) ActionManager.getInstance().getAction(FindBugsPluginConstants.ACTIVE_CHANGELIST_ACTION);
		ChangeListManager.getInstance(_project).removeChangeListListener(action.getChangelistAdapter());
		unregisterToolWindow();
		disableToolbarActions();
		ActionManager.getInstance().unregisterAction(FindBugsPluginConstants.FINDBUGS_PROJECT_HOLDER);
	}


	private void initToolWindow() {
		final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(_project);

		_toolWindow = toolWindowManager.registerToolWindow(getInternalToolWindowId(), false, ToolWindowAnchor.BOTTOM);
		_toolWindow.setTitle(FindBugsPluginConstants.TOOL_WINDOW_ID);
		_toolWindow.setType(ToolWindowType.DOCKED, null);


		// TODO: facade
		//final ContentFactory contentFactory = PeerFactory.getInstance().getContentFactory();
		final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

		final JComponent toolWindowPanel = new ToolWindowPanel(_project, _toolWindow);
		final Content content = contentFactory.createContent(toolWindowPanel, "FindBugs Analysis Results", false);
		//final Content content1 = contentFactory.createContent(toolWindowPanel, "Found Bugs View", false);
		//final Content content1 = contentFactory.createContent(_contentPanel1, "Bug Details", false);
		//final JComponent toolWindowPanel1 = new ToolWindowPanel(_project, _toolWindow);
		//final Content content1 = contentFactory.createContent(toolWindowPanel1, "Found Bugs View (import)", true);
		//content1.setPinned();

		_toolWindow.getContentManager().addContent(content);
		//_toolWindow.getContentManager().addContent(content1);
		//_toolWindow.getContentManager().addContent(content1);
		_toolWindow.setIcon(GuiResources.FINDBUGS_ICON);
	}


	public String getInternalToolWindowId() {
		return FindBugsPluginConstants.TOOL_WINDOW_ID;
		//return new StringBuilder(FindBugsPluginConstants.TOOL_WINDOW_ID).append("#").append(_project.getName()).toString();
	}


	private void unregisterToolWindow() {
		final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(_project);
		toolWindowManager.unregisterToolWindow(getInternalToolWindowId());
	}


	public Project getProject() {
		return _project;
	}


	public Module getModule() {
		return null;
	}


	public boolean isModuleComponent() {
		return false;
	}


	/**
	 * Get the tool window panel for result display.
	 *
	 * @return the tool window panel.
	 */
	public ToolWindowPanel getToolWindowPanel() {
		final Content content = _toolWindow.getContentManager().getContent(0);
		if (content != null) {
			return (ToolWindowPanel) content.getComponent();
		}
		return null;
	}


	public void activateToolWindow(final boolean activate) {
		if (activate) {
			_toolWindow.show(null);
		} else {
			_toolWindow.hide(null);
		}
	}


	public BugCollection getBugCollection() {
		return getToolWindowPanel().getBugCollection();
	}


	public Map<PsiFile, List<ExtendedProblemDescriptor>> getProblems() {
		return getToolWindowPanel().getProblems();
	}


	private static void registerToolbarActions() {
		final DefaultActionGroup mainToolbar = (DefaultActionGroup) ActionManager.getInstance().getAction("MainToolBar");

		//ActionManager.getInstance().registerAction("FindBugs.CurrentFileAction", action);
		for (final AnAction anAction : _toolbarActions) {
			if (!isActionRegistered(anAction)) {
				_registeredActions.add(anAction);
				mainToolbar.add(anAction, new Constraints(Anchor.BEFORE, "RunConfiguration"));

			}
		}
	}


	@SuppressWarnings({"MethodMayBeStatic"})
	private void setActionGroupsIcon() {
		final AnAction findBugsEditorPopup = ActionManager.getInstance().getAction("FindBugs.EditorPopup");
		findBugsEditorPopup.getTemplatePresentation().setIcon(GuiResources.FINDBUGS_ICON);

		final AnAction findBugsProjectViewPopup = ActionManager.getInstance().getAction("FindBugs.ProjectViewPopupMenu");
		findBugsProjectViewPopup.getTemplatePresentation().setIcon(GuiResources.FINDBUGS_ICON);

		final AnAction findBugsAnalyzeMenu = ActionManager.getInstance().getAction("FindBugs.AnalyzeMenu");
		findBugsAnalyzeMenu.getTemplatePresentation().setIcon(GuiResources.FINDBUGS_ICON);
	}


	private static void disableToolbarActions() {
		//final DefaultActionGroup mainToolbar = (DefaultActionGroup) ActionManager.getInstance().getAction("MainToolBar");
		for (final AnAction action : _toolbarActions) {
			//mainToolbar.remove(action);
			action.getTemplatePresentation().setEnabled(false);
		}
		//_registeredActions.clear();
	}


	private static boolean isActionRegistered(final AnAction anAction) {
		return _registeredActions.contains(anAction);
	}


	/**
	 * Process an error.
	 *
	 * @param message a description of the error. May be null.
	 * @param error   the exception.
	 * @return any exception to be passed upwards.
	 */
	@SuppressWarnings({"ThrowableInstanceNeverThrown"})
	public static FindBugsPluginException processError(final String message, @Nullable final Throwable error) {
		Throwable root = error;

		while (root != null && root.getCause() != null && !(root instanceof AnalysisException)) {
			root = root.getCause();
		}

		if (message != null) {
			return new FindBugsPluginException(message, root);
		}

		if (root != null) {
			return new FindBugsPluginException(root.getMessage(), root);
		}

		return new FindBugsPluginException("Unknown error..."); // this should never happen
	}


	@SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
	public static void processError(final Map<String, Map<String, Throwable>> status) {
		/*for (final Entry<String, Map<String, Throwable>> error : status.entrySet()) {


			//for()
			//processError(error.getKey(), error.getValue().);
		}*/
	}


	public static void showToolWindowNotifier(final String message, final MessageType type) {
		if (MessageType.INFO.equals(type)) {
			BalloonTipFactory.showToolWindowInfoNotifier(message);
		} else if (MessageType.WARNING.equals(type)) {
			BalloonTipFactory.showToolWindowWarnNotifier(message);
		} else if (MessageType.ERROR.equals(type)) {
			BalloonTipFactory.showToolWindowErrorNotifier(message);
		}
	}


	public FindBugsPreferences getPreferences() {
		if (_preferences == null) {
			_preferences = getEmptyPreferences(Collections.<String>emptyList());
		}
		//noinspection ReturnOfCollectionOrArrayField
		return _preferences;
	}


	/**
	 * ==========================================================================
	 * Plugin Configuration
	 */

	@Nls
	public String getDisplayName() {
		return ResourcesLoader.getString("findbugs.plugin.configuration.name");
	}


	public Icon getIcon() {
		return GuiResources.FINDBUGS_CONFIGURATION_ICON;
	}


	public String getHelpTopic() {
		return FindBugsPluginConstants.FINDBUGS_EXTERNAL_HELP_URI;
	}


	public JComponent createComponent() {
		if (_configPanel == null) {
			_configPanel = new ConfigurationPanel(this);
		}
		return _configPanel;
	}


	public boolean isModified() {
		return _preferences.isModified();
	}


	public void apply() throws ConfigurationException {
		_preferences.setModified(false);
	}


	public void reset() {
		//TODO: what??
	}


	public void disposeUIResources() {
		if (_configPanel != null) {
			_configPanel.setVisible(false);
		}
		//noinspection AssignmentToNull
		_configPanel = null;
	}


	private synchronized FindBugsPreferences getDefaultPreferences() {
		if (_preferences == null) {
			_preferences = FindBugsPreferences.createDefault();
		}
		return _preferences;
	}


	private synchronized FindBugsPreferences getEmptyPreferences(final List<String> pluginList) {
		if (_preferences == null) {
			_preferences = FindBugsPreferences.createEmpty(pluginList);
		}
		return _preferences;
	}


	public void loadState(final PersistencePreferencesBean state) {
		//_preferences.clear();

		if (!state.isEmpty()) {
			_preferences = getEmptyPreferences(state.getPlugins());
			//_preferences.clear();
			_preferences.setPlugins(state.getPlugins()); // needs to be set first before DetectorFactoryCollection instance is created first
			_preferences.setEnabledPlugins(state.getEnabledPlugins());
			_preferences.setDisabledPlugins(state.getDisabledPlugins());

			for (final String key : state.getBasePreferences().keySet()) {
				_preferences.setProperty(key, state.getBasePreferences().get(key));
			}

			final Map<String, String> detectors = state.getDetectors();
			_preferences.setDetectors(detectors);
			for (final Entry<String, String> entry : detectors.entrySet()) {
				final DetectorFactory detectorFactory = FindBugsPreferences.getDetectorFactorCollection().getFactory(entry.getKey());
				if (detectorFactory != null) {
					_preferences.getUserPreferences().enableDetector(detectorFactory, Boolean.valueOf(entry.getValue()));
				}
			}

			_preferences.setBugCategories(state.getBugCategories());
			_preferences.setIncludeFilters(state.getIncludeFilters());
			_preferences.setExcludeFilters(state.getExcludeFilters());
			_preferences.setExcludeBaselineBugs(state.getExcludeBaselineBugs());

			_preferences.setEnabledModuleConfigs(state.getEnabledModuleConfigs());

			if (_preferences.getBugCategories().isEmpty()) {
				_preferences.setBugCategories(FindBugsPreferences.getDefaultBugCategories(_preferences.getUserPreferences().getFilterSettings()));
			}
			if (_preferences.getDetectors().isEmpty()) {
				LOGGER.debug("empty detectors loading defaults.");
				_preferences.setDetectors(FindBugsPreferences.getAvailableDetectors(_preferences.getUserPreferences()));
			}
		} else {
			_preferences.clear();
			_preferences = FindBugsPreferences.createDefault();
		}
	}


	public PersistencePreferencesBean getState() {
		final PersistencePreferencesBean preferencesBean = new PersistencePreferencesBean();
		if (_preferences == null) {
			_preferences = getDefaultPreferences();
		}

		for (final Enumeration<?> confNames = _preferences.propertyNames(); confNames.hasMoreElements();) {
			final String elementName = (String) confNames.nextElement();
			preferencesBean.getBasePreferences().put(elementName, _preferences.getProperty(elementName));
		}
		preferencesBean.getDetectors().putAll(_preferences.getDetectors());
		preferencesBean.getBugCategories().putAll(_preferences.getBugCategories());

		preferencesBean.getIncludeFilters().addAll(_preferences.getIncludeFilters());
		preferencesBean.getExcludeFilters().addAll(_preferences.getExcludeFilters());
		preferencesBean.getExcludeBaselineBugs().addAll(_preferences.getExcludeBaselineBugs());
		preferencesBean.getPlugins().addAll(_preferences.getPlugins());
		preferencesBean.getEnabledPlugins().addAll(_preferences.getEnabledPlugins());
		preferencesBean.getDisabledPlugins().addAll(_preferences.getDisabledPlugins());

		preferencesBean.getEnabledModuleConfigs().addAll(_preferences.getEnabledModuleConfigs());

		return preferencesBean;
	}
}
