/*
 * Copyright 2008-2015 Andre Pfeiler
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
package org.twodividedbyzero.idea.findbugs.messages;


import com.intellij.util.messages.Topic;
import edu.umd.cs.findbugs.BugCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.twodividedbyzero.idea.findbugs.core.FindBugsProject;


/**
 * @author Reto Merz<reto.merz@gmail.com>
 * @version $Revision: 364 $
 * @since 0.9.995
 */
public interface AnalysisFinishedListener {
	Topic<AnalysisFinishedListener> TOPIC = Topic.create("FindBugs Analysis Finished", AnalysisFinishedListener.class);

	/**
	 * Invoked by EDT.
	 */
	void analysisFinished(@NotNull final BugCollection bugCollection, @Nullable final FindBugsProject findBugsProject);

}