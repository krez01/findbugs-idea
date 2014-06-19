/*
 * Copyright 2008-2013 Andre Pfeiler
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

package org.twodividedbyzero.idea.findbugs.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import edu.umd.cs.findbugs.BugCollection;
import edu.umd.cs.findbugs.HTMLBugReporter;
import org.dom4j.Document;
import org.dom4j.io.DocumentSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.common.EventDispatchThreadHelper;
import org.twodividedbyzero.idea.findbugs.common.FindBugsPluginConstants;
import org.twodividedbyzero.idea.findbugs.common.event.EventListener;
import org.twodividedbyzero.idea.findbugs.common.event.EventManagerImpl;
import org.twodividedbyzero.idea.findbugs.common.event.filters.BugReporterEventFilter;
import org.twodividedbyzero.idea.findbugs.common.event.types.BugReporterEvent;
import org.twodividedbyzero.idea.findbugs.common.exception.FindBugsPluginException;
import org.twodividedbyzero.idea.findbugs.common.util.IdeaUtilImpl;
import org.twodividedbyzero.idea.findbugs.common.util.IoUtil;
import org.twodividedbyzero.idea.findbugs.core.FindBugsPluginImpl;
import org.twodividedbyzero.idea.findbugs.gui.common.ExportFileDialog;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;
import org.twodividedbyzero.idea.findbugs.tasks.BackgroundableTask;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @author Keith Lea <keithl@gmail.com>
 * @version $Revision$
 * @since 0.9.95
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class ExportBugCollection extends BaseAction implements EventListener<BugReporterEvent> {

	private static final Logger LOGGER = Logger.getInstance(ExportBugCollection.class.getName());

	private static final String FINDBUGS_PLAIN_XSL = "plain.xsl";
	private static final String FINDBUGS_RESULT_PREFIX = "FindBugsResult_";
	private static final String FINDBUGS_RESULT_HTML_SUFFIX = ".html";
	private static final String FINDBUGS_RESULT_RAW_SUFFIX = ".xml";

	private boolean _enabled;
	private BugCollection _bugCollection;
	private boolean _running;
	private static final Pattern PATTERN = Pattern.compile("[/ :]");


	@Override
	protected boolean isEnabled() {
		return _enabled;
	}


	@Override
	protected boolean setEnabled(final boolean enabled) {
		final boolean was = _enabled;
		if (_enabled != enabled) {
			_enabled = enabled;
		}
		return was;
	}


	@SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
	@Override
	public void actionPerformed(final AnActionEvent e) {
		final Project project = IdeaUtilImpl.getProject(e.getDataContext());
		assert project != null;
		final Presentation presentation = e.getPresentation();

		// check a project is loaded
		if (isProjectNotLoaded(project, presentation)) {
			Messages.showWarningDialog("Project not loaded.", "FindBugs");  // NON-NLS
			return;
		}

		final FindBugsPreferences preferences = getPluginInterface(project).getPreferences();
		String exportDir = preferences.getProperty(FindBugsPreferences.EXPORT_BASE_DIR, FindBugsPluginConstants.DEFAULT_EXPORT_DIR);
		boolean exportXml = preferences.getBooleanProperty(FindBugsPreferences.EXPORT_AS_XML, true);
		boolean exportHtml = preferences.getBooleanProperty(FindBugsPreferences.EXPORT_AS_HTML, true);
		boolean exportBoth = exportXml && preferences.getBooleanProperty(FindBugsPreferences.EXPORT_AS_HTML, true);

		if (exportDir.isEmpty() || !exportXml && !exportBoth && !exportHtml) {

			//Ask the user for a export directory
			final DialogBuilder dialogBuilder = new DialogBuilder(project);
			dialogBuilder.addOkAction();
			dialogBuilder.addCancelAction();
			dialogBuilder.setTitle("Select directory to save the exported file");
			final ExportFileDialog exportDialog = new ExportFileDialog(exportDir, dialogBuilder);
			dialogBuilder.showModal(true);
			if (dialogBuilder.getDialogWrapper().getExitCode() == DialogWrapper.CANCEL_EXIT_CODE) {
				return;
			}
			final String path = exportDialog.getText();
			if (path == null || path.trim().isEmpty()) {
				return;
			}

			exportXml = exportDialog.isXml() != exportXml ? exportDialog.isXml() : exportXml;
			exportHtml = exportDialog.isXml() == exportHtml ? !exportDialog.isXml() : exportHtml;
			exportBoth = exportDialog.isBoth() != exportBoth ? exportDialog.isBoth() : exportBoth;
			exportDir = path.trim();
		}
		//Create a unique file name by using time stamp
		final Date currentDate = new Date();
		final String timestamp = PATTERN.matcher(new SimpleDateFormat().format(currentDate)).replaceAll("_");
		final String fileName = File.separatorChar + FINDBUGS_RESULT_PREFIX + timestamp;

		final boolean finalExportXml = exportXml;
		final boolean finalExportHtml = exportHtml;
		final boolean finalExportBoth = exportBoth;
		final String finalExportDir = exportDir + File.separatorChar + project.getName();

		//Create a task to export the bug collection to html
		final AtomicReference<Task> exportTask = new AtomicReference<Task>(new BackgroundableTask(project, "Exporting Findbugs Result", false) {
			private ProgressIndicator _indicator;


			@edu.umd.cs.findbugs.annotations.SuppressFBWarnings({"REC_CATCH_EXCEPTION"})
			@SuppressWarnings({"IOResourceOpenedButNotSafelyClosed"})
			@Override
			public void run(@NotNull final ProgressIndicator indicator) {
				indicator.setText2(finalExportDir + File.separatorChar + fileName);
				setProgressIndicator(indicator);
				Writer writer = null;
				try {
					createDirIfAbsent(project, finalExportDir);
					String exportDir = finalExportDir;
					final boolean createSubDir = preferences.getBooleanProperty(FindBugsPreferences.EXPORT_CREATE_ARCHIVE_DIR, true);
					if(createSubDir) {
						exportDir = finalExportDir + File.separatorChar + new SimpleDateFormat("yyyy_MM_dd", Locale.ENGLISH).format(currentDate);
						createDirIfAbsent(project, exportDir);
					}

					if (_bugCollection != null) {
						_bugCollection.setWithMessages(true);
						final String exportDirAndFilenameWithoutSuffix = exportDir + fileName;
						if (finalExportXml && !finalExportBoth) {
							_bugCollection.writeXML(exportDirAndFilenameWithoutSuffix + FINDBUGS_RESULT_RAW_SUFFIX);
						} else if (finalExportBoth) {
							_bugCollection.writeXML(exportDirAndFilenameWithoutSuffix + FINDBUGS_RESULT_RAW_SUFFIX);
							writer = exportHtml(exportDirAndFilenameWithoutSuffix + FINDBUGS_RESULT_HTML_SUFFIX);
						} else if (finalExportHtml) {
							writer = exportHtml(exportDirAndFilenameWithoutSuffix + FINDBUGS_RESULT_HTML_SUFFIX);
						}
						_bugCollection.setWithMessages(false);

						showToolWindowNotifier(project, "Exported bug collection to " + exportDir + '.', MessageType.INFO);
						if((!finalExportXml || finalExportBoth) && preferences.getBooleanProperty(FindBugsPreferences.EXPORT_OPEN_BROWSER, true)) {
							BrowserUtil.launchBrowser(new File(exportDirAndFilenameWithoutSuffix + FINDBUGS_RESULT_HTML_SUFFIX).getAbsolutePath());
						}
					}
				} catch (final IOException e1) {
					final String message = "Export failed";
					showToolWindowNotifier(project, message, MessageType.ERROR);
					LOGGER.error(message, e1);
				} catch (final TransformerConfigurationException e1) {
					final String message = "Transform to html failed due to configuration problems.";
					showToolWindowNotifier(project, message, MessageType.ERROR);
					LOGGER.error(message, e1);
				} catch (final TransformerException e1) {
					final String message = "Transformation to exportXml failed.";
					showToolWindowNotifier(project, message, MessageType.ERROR);
					LOGGER.error(message, e1);
				} catch (final Exception e) {
					showToolWindowNotifier(project, e.getMessage(), MessageType.ERROR);
					LOGGER.error(e.getMessage(), e);
				} finally {
					IoUtil.safeClose(writer);
					Thread.currentThread().interrupt();
				}
			}


			@Override
			public void setProgressIndicator(@NotNull final ProgressIndicator indicator) {
				_indicator = indicator;
			}


			@Override
			public ProgressIndicator getProgressIndicator() {
				return _indicator;
			}
		});

		final File file = new File(exportDir + fileName);
		if (file.getParentFile() == null) {
			showToolWindowNotifier(project, "Exporting bug collection failed. not a directory. " + exportDir + fileName + '.', MessageType.ERROR);
		} else {
			exportTask.get().queue();
		}
	}


	@Nullable
	private Writer exportHtml(final String fileName) throws IOException, TransformerException {
		final Document document = _bugCollection.toDocument();
		final Source xsl = new StreamSource(getStylesheetStream(FINDBUGS_PLAIN_XSL));
		xsl.setSystemId(FINDBUGS_PLAIN_XSL);

		// Create a transformer using the stylesheet
		final Transformer transformer = TransformerFactory.newInstance().newTransformer(xsl);

		// Source document is the XML generated from the BugCollection
		final Source source = new DocumentSource(document);

		// Write result to output stream
		final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(fileName), Charset.forName("UTF-8").newEncoder());
		final Result result = new StreamResult(writer);
		// Do the transformation
		transformer.transform(source, result);
		return writer;

	}


	private static void createDirIfAbsent(final Project project, final String dir) {
		final File exportDir = new File(dir);
		if(!exportDir.exists()) {
			if(!exportDir.mkdirs()) {
				final String message = "Creating the export directory '" + exportDir + "' failed.";
				showToolWindowNotifier(project, message, MessageType.ERROR);
				LOGGER.error(message);
			}
		}
	}


	private static void showToolWindowNotifier(final Project project, final String message, final MessageType type) {
		EventDispatchThreadHelper.invokeLater(new Runnable() {
			public void run() {
				FindBugsPluginImpl.showToolWindowNotifier(project, message, type);
			}
		});
	}


	private static InputStream getStylesheetStream(final String stylesheet) throws IOException {
		try {
			final URL url = new URL(stylesheet);
			return url.openStream();
		} catch (final Exception e) {
			LOGGER.info("xls read failed.", e);
		}
		try {
			return new BufferedInputStream(new FileInputStream(stylesheet));
		} catch (final Exception ignored) {
		}
		final InputStream xslInputStream = HTMLBugReporter.class.getResourceAsStream('/' + stylesheet);
		if (xslInputStream == null) {
			throw new IOException("Could not load HTML generation stylesheet " + stylesheet);
		}
		return xslInputStream;
	}


	@Override
	public void update(final AnActionEvent event) {
		try {
			final Project project = DataKeys.PROJECT.getData(event.getDataContext());
			final Presentation presentation = event.getPresentation();

			// check a project is loaded
			if (isProjectNotLoaded(project, presentation)) {
				return;
			}

			isPluginAccessible(project);

			// check if tool window is registered
			final ToolWindow toolWindow = isToolWindowRegistered(project);
			if (toolWindow == null) {
				presentation.setEnabled(false);
				presentation.setVisible(false);

				return;
			}

			registerEventListener(project);

			if (!_running) {
				_enabled = _bugCollection != null && _bugCollection.iterator().hasNext();
			}

			presentation.setEnabled(toolWindow.isAvailable() && isEnabled());
			presentation.setVisible(true);

		} catch (final Throwable e) {
			final FindBugsPluginException processed = FindBugsPluginImpl.processError("Action update failed", e);
			LOGGER.error("Action update failed", processed);
		}
	}


	@SuppressWarnings({"AssignmentToNull"})
	public void onEvent(@NotNull final BugReporterEvent event) {
		switch (event.getOperation()) {
			case ANALYSIS_STARTED:
				_bugCollection = null;
				setEnabled(false);
				setRunning(true);
				break;
			case ANALYSIS_ABORTED:
				_bugCollection = null;
				setEnabled(true);
				setRunning(false);
				break;
			case ANALYSIS_FINISHED:
				_bugCollection = event.getBugCollection();
				setEnabled(true);
				setRunning(false);
				break;
			case NEW_BUG_INSTANCE:
				break;
			default:
		}
	}


	private void registerEventListener(final Project project) {
		final String projectName = project.getName();
		if (!isRegistered(projectName)) {
			EventManagerImpl.getInstance().addEventListener(new BugReporterEventFilter(projectName), this);
			addRegisteredProject(projectName);
		}
	}


	protected boolean isRunning() {
		return _running;
	}


	boolean setRunning(final boolean running) {
		final boolean was = _running;
		if (_running != running) {
			_running = running;
		}
		return was;
	}
}
