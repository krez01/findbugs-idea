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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.core.FindBugsPlugin;
import org.twodividedbyzero.idea.findbugs.core.FindBugsState;
import org.twodividedbyzero.idea.findbugs.gui.toolwindow.view.ToolWindowPanel;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;


/**
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.0.1
 */
public final class ScrollToSource extends AbstractToggleAction {

	@Override
	boolean isSelectedImpl(
			@NotNull final AnActionEvent e,
			@NotNull final Project project,
			@Nullable final Module module,
			@NotNull final FindBugsPlugin plugin,
			@NotNull final ToolWindow toolWindow,
			@NotNull final FindBugsState state,
			@NotNull final FindBugsPreferences preferences) {

		final Content content = toolWindow.getContentManager().getContent(0);
		if (content != null) {
			final ToolWindowPanel panel = (ToolWindowPanel)content.getComponent();
			final boolean isEnabled = panel.getBugTreePanel().isScrollToSource();
			final boolean enabled = preferences.getBooleanProperty(FindBugsPreferences.TOOLWINDOW_SCROLL_TO_SOURCE, isEnabled);
			if(enabled != isEnabled) {
				panel.getBugTreePanel().setScrollToSource(enabled);
			}
			return enabled;
		}
		return false;
	}


	@Override
	void setSelectedImpl(
			@NotNull final AnActionEvent e,
			@NotNull final Project project,
			@Nullable final Module module,
			@NotNull final FindBugsPlugin plugin,
			@NotNull final ToolWindow toolWindow,
			@NotNull final FindBugsState state,
			@NotNull final FindBugsPreferences preferences,
			final boolean select) {

		final Content content = toolWindow.getContentManager().getContent(0);
		if (content != null) {
			final ToolWindowPanel panel = (ToolWindowPanel) content.getComponent();
			preferences.setProperty(FindBugsPreferences.TOOLWINDOW_SCROLL_TO_SOURCE, select);
			panel.getBugTreePanel().setScrollToSource(select);
		}
	}
}