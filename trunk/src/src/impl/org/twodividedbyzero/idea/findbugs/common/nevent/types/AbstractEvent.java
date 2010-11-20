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
package org.twodividedbyzero.idea.findbugs.common.nevent.types;

import org.twodividedbyzero.idea.findbugs.common.nevent.Event;


/**
 * See the Event interface.
 * <p/>
 * $Date$
 *
 * @author Andre Pfeiler<andrep@twodividedbyzero.org>
 * @version $Revision$
 * @since 0.9.7-dev
 */
public abstract class AbstractEvent<T> implements Event<T> {
	//private static final long serialVersionUID = 1L;

	private final String _description;

	private T _result;

	//private Utils utils_ = new Utils();


	AbstractEvent(final String description) {
		_description = description;
	}


	public String getDescription() {
		return _description;
	}


	public T getResult() {
		return _result;
	}


	public void setResult(final T result) {
		_result = result;
	}


	@Override
	public String toString() {
		return "AbstractEvent{" + "_description='" + _description + '\'' + ", _result=" + _result + '}';
	}
}
