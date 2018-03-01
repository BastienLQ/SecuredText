/**
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.smssecure.smssecure;


import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.smssecure.smssecure.components.RecyclerViewFastScroller;
import org.smssecure.smssecure.contacts.ContactSelectionListAdapter;
import org.smssecure.smssecure.contacts.ContactSelectionListItem;
import org.smssecure.smssecure.contacts.ContactsCursorLoader;
import org.smssecure.smssecure.database.CursorRecyclerViewAdapter;
import org.smssecure.smssecure.util.StickyHeaderDecoration;
import org.smssecure.smssecure.util.ViewUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Fragment for selecting a one or more contacts from a list.
 *
 * @author Moxie Marlinspike
 *
 */
public class ContactSelectionListFragment extends    Fragment
                                          implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG = ContactSelectionListFragment.class.getSimpleName();

  private TextView emptyText;

  private Map<Long, String>         selectedContacts;
  private OnContactSelectedListener onContactSelectedListener;
  private String                    cursorFilter;
  private RecyclerView              recyclerView;
  private RecyclerViewFastScroller  fastScroller;

  private boolean                   multi = false;

  @Override
  public void onActivityCreated(Bundle icicle) {
    super.onCreate(icicle);
    initializeCursor();
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onPause() {
    super.onPause();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.contact_selection_list_fragment, container, false);

    emptyText    = ViewUtil.findById(view, android.R.id.empty);
    recyclerView = ViewUtil.findById(view, R.id.recycler_view);
    fastScroller = ViewUtil.findById(view, R.id.fast_scroller);
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

    return view;
  }

  public List<String> getSelectedContacts() {
    if (selectedContacts == null) return null;

    List<String> selected = new LinkedList<>();
    selected.addAll(selectedContacts.values());

    return selected;
  }

  public void setMultiSelect(boolean multi) {
    this.multi = multi;
  }

  private void initializeCursor() {
    ContactSelectionListAdapter adapter = new ContactSelectionListAdapter(getActivity(),
                                                                          null,
                                                                          new ListClickListener(),
                                                                          multi);
    selectedContacts = adapter.getSelectedContacts();
    recyclerView.setAdapter(adapter);
    recyclerView.addItemDecoration(new StickyHeaderDecoration(adapter, true, true));
    this.getLoaderManager().initLoader(0, null, this);
  }

  public void setQueryFilter(String filter) {
    this.cursorFilter = filter;
    this.getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new ContactsCursorLoader(getActivity(), true, cursorFilter);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    ((CursorRecyclerViewAdapter) recyclerView.getAdapter()).changeCursor(data);
    emptyText.setText(R.string.contact_selection_group_activity__no_contacts);
    if (recyclerView.getAdapter().getItemCount() > 1) emptyText.setVisibility(View.GONE);
    boolean useFastScroller = (recyclerView.getAdapter().getItemCount() > 20);
    recyclerView.setVerticalScrollBarEnabled(!useFastScroller);
    if (useFastScroller) {
      fastScroller.setVisibility(View.VISIBLE);
      fastScroller.setRecyclerView(recyclerView);
    }
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((CursorRecyclerViewAdapter) recyclerView.getAdapter()).changeCursor(null);
    fastScroller.setVisibility(View.GONE);
  }

  private class ListClickListener implements ContactSelectionListAdapter.ItemClickListener {
    public void onItemClick(ContactSelectionListItem contact) {

      if (!multi || !selectedContacts.containsKey(contact.getContactId())) {
        selectedContacts.put(contact.getContactId(), contact.getNumber());
        contact.setChecked(true);
        if (onContactSelectedListener != null) onContactSelectedListener.onContactSelected(contact.getNumber());
      } else {
        selectedContacts.remove(contact.getContactId());
        contact.setChecked(false);
      }
    }
  }

  public void setOnContactSelectedListener(OnContactSelectedListener onContactSelectedListener) {
    this.onContactSelectedListener = onContactSelectedListener;
  }

  public interface OnContactSelectedListener {
    public void onContactSelected(String number);
  }

}
