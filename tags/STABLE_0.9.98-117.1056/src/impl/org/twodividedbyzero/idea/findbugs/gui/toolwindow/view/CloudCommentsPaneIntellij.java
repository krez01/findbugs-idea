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

package org.twodividedbyzero.idea.findbugs.gui.toolwindow.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import edu.umd.cs.findbugs.cloud.CloudPlugin;
import edu.umd.cs.findbugs.gui2.CloudCommentsPane;
import org.twodividedbyzero.idea.findbugs.common.util.GuiUtil;
import org.twodividedbyzero.idea.findbugs.core.FindBugsPlugin;
import org.twodividedbyzero.idea.findbugs.preferences.FindBugsPreferences;

import java.awt.Graphics;
import java.util.List;


@SuppressWarnings({"override"})
public class CloudCommentsPaneIntellij extends CloudCommentsPane {

	private static final Logger LOGGER = Logger.getInstance(CloudCommentsPane.class.getName());

	private final ToolWindowPanel _toolWindowPanel;


	public CloudCommentsPaneIntellij(final ToolWindowPanel toolWindowPanel) {
		_toolWindowPanel = toolWindowPanel;
	}


	@Override
	protected void paintComponent(final Graphics g) {
		GuiUtil.configureGraphics(g);
		super.paintComponent(g);
	}


	@Override
	protected void paintChildren(final Graphics g) {
		GuiUtil.configureGraphics(g);
		super.paintChildren(g);
	}


	protected void setSignInOutText(final String buttonText) {
		((LinkLabel) signInOutLink).setText(buttonText);
	}


	protected void setupLinksOrButtons() {
		/* _addCommentLink = new LinkLabel();
				((LinkLabel)_addCommentLink).setText("add comment");
				((LinkLabel)_addCommentLink).setListener(new LinkListener() {
					public void linkSelected(final LinkLabel linkLabel, final Object o) {
						addCommentClicked();
					}
				}, null);*/
		cancelLink = new LinkLabel();
		((LinkLabel) cancelLink).setText("cancel");
		((LinkLabel) cancelLink).setListener(new LinkListener() {
			public void linkSelected(final LinkLabel linkLabel, final Object o) {
				cancelClicked();
			}
		}, null);
		signInOutLink = new LinkLabel();
		((LinkLabel) signInOutLink).setText("sign in");
		((LinkLabel) signInOutLink).setListener(new LinkListener() {
			public void linkSelected(final LinkLabel linkLabel, final Object o) {
				signInOrOutClicked();
			}
		}, null);
		/*changeLink = new LinkLabel();
				((LinkLabel)_changeLink).setText("change");
				((LinkLabel)_changeLink).setListener(new LinkListener() {
					public void linkSelected(final LinkLabel linkLabel, final Object o) {
						changeClicked();
					}
				}, null);*/

	}


	protected void showCloudChooser(final List<CloudPlugin> plugins, final List<String> descriptions) {
		final JBPopupFactory factory = JBPopupFactory.getInstance();
		final ListPopup popup = factory.createListPopup(new BaseListPopupStep<String>("Store comments in:", descriptions) {
			@Override
			public PopupStep<?> onChosen(final String selectedValue, final boolean finalChoice) {
				if (selectedValue != null) {
					final int index = descriptions.indexOf(selectedValue);
					if (index == -1) {
						LOGGER.error("Error - not found - '" + selectedValue + "' among " + descriptions);
					} else {
						final CloudPlugin newPlugin = plugins.get(index);
						final String newCloudId = newPlugin.getId();
						changeCloud(newCloudId);
					}
				}
				return super.onChosen(selectedValue, finalChoice);
			}


			@Override
			public void canceled() {
				super.canceled();
			}
		});
		popup.showInCenterOf(signInOutLink);
	}


	protected boolean isDisabled(CloudPlugin plugin) {
		final FindBugsPlugin findBugsPlugin = _toolWindowPanel.getProject().getComponent(FindBugsPlugin.class);
		final FindBugsPreferences prefs = findBugsPlugin.getPreferences();
		return prefs.isPluginDisabled(plugin.getFindbugsPluginId());
	}


	protected void issueUpdated() {
		_toolWindowPanel.getBugDetailsComponents().issueUpdated(_bugInstance);
	}
}
