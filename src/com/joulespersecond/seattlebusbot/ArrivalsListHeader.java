package com.joulespersecond.seattlebusbot;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.ArrayList;

//
// A helper class that gets most of the header interaction
// out of the Fragment itself.
//
class ArrivalsListHeader {
    interface Controller {
        String getStopName();
        String getStopDirection();

        String getUserStopName();
        void setUserStopName(String userName);

        long getLastGoodResponseTime();

        ArrayList<String> getRoutesFilter();
        void setRoutesFilter(ArrayList<String> filter);
        int getNumRoutes();

        boolean isFavorite();
        boolean setFavorite(boolean favorite);
    }

    private Controller mController;
    private Context mContext;
    //
    // Cached views
    //
    private View mView;
    private View mNameContainerView;
    private View mEditNameContainerView;
    private TextView mNameView;
    private EditText mEditNameView;
    private ImageButton mFavoriteView;
    private View mDirectionView;
    private View mFilterGroup;
    private TextView mResponseError;
    private boolean mInNameEdit = false;

    ArrivalsListHeader(Context context, Controller controller) {
        mController = controller;
        mContext = context;
    }

    void initView(View view) {
        mView = view;
        mNameContainerView = mView.findViewById(R.id.name_container);
        mEditNameContainerView = mView.findViewById(R.id.edit_name_container);
        mNameView = (TextView)mView.findViewById(R.id.stop_name);
        mEditNameView = (EditText)mView.findViewById(R.id.edit_name);
        mFavoriteView = (ImageButton)mView.findViewById(R.id.stop_favorite);
        mDirectionView = mView.findViewById(R.id.direction);
        mFilterGroup = mView.findViewById(R.id.filter_group);
        mResponseError = (TextView)mView.findViewById(R.id.response_error);
        mFavoriteView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setFavorite(!mController.isFavorite());
                refreshFavorite();
            }
        });

        mNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                beginNameEdit(null);
            }
        });

        // Implement the "Save" and "Clear" buttons
        View save = mView.findViewById(R.id.edit_name_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setUserStopName(mEditNameView.getText().toString());
                endNameEdit();
            }
        });

        mEditNameView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    mController.setUserStopName(mEditNameView.getText().toString());
                    endNameEdit();
                    return true;
                }
                return false;
            }
        });

        // "Cancel"
        View cancel = mView.findViewById(R.id.edit_name_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endNameEdit();
            }
        });

        View clear = mView.findViewById(R.id.edit_name_revert);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.setUserStopName(null);
                endNameEdit();
            }
        });
        UIHelp.setChildClickable(mView, R.id.show_all, mShowAllClick);
    }

    private final ClickableSpan mShowAllClick = new ClickableSpan() {
        public void onClick(View v) {
            mController.setRoutesFilter(new ArrayList<String>());
            refreshFilter();
        }
    };

    void refresh() {
        refreshName();
        refreshDirection();
        refreshFavorite();
        refreshFilter();
        refreshError();
    }

    private void refreshName() {
        String name = mController.getStopName();
        String userName = mController.getUserStopName();

        if (!TextUtils.isEmpty(userName)) {
            mNameView.setText(userName);
        } else if (name != null) {
            mNameView.setText(name);
        }
    }

    private void refreshDirection() {
        String direction = mController.getStopDirection();
        if (direction != null) {
            final int directionText = UIHelp.getStopDirectionText(direction);
            ((TextView)mDirectionView).setText(directionText);
            if (directionText != R.string.direction_none && !mInNameEdit) {
                mDirectionView.setVisibility(View.VISIBLE);
            } else {
                mDirectionView.setVisibility(View.GONE);
            }
        }
    }

    private void refreshFavorite() {
        mFavoriteView.setImageResource(mController.isFavorite() ?
                R.drawable.focus_star_on :
                R.drawable.focus_star_off);
    }

    private void refreshFilter() {
        TextView v = (TextView)mView.findViewById(R.id.filter);
        ArrayList<String> routesFilter = mController.getRoutesFilter();
        final int num = (routesFilter != null) ? routesFilter.size() : 0;
        if (num > 0) {
            final int total = mController.getNumRoutes();
            v.setText(mContext.getString(R.string.stop_info_filter_header, num, total));
            // Show the filter text (except when in name edit mode)
            mFilterGroup.setVisibility(mInNameEdit ? View.GONE : View.VISIBLE);
        } else {
            mFilterGroup.setVisibility(View.GONE);
        }
    }

    private void refreshError() {
        final long now = System.currentTimeMillis();
        final long responseTime = mController.getLastGoodResponseTime();

        if ((responseTime) != 0 &&
                ((now - responseTime) >= 2 * DateUtils.MINUTE_IN_MILLIS)) {
            CharSequence relativeTime =
                DateUtils.getRelativeTimeSpanString(responseTime,
                        now,
                        DateUtils.MINUTE_IN_MILLIS,
                        0);
            mResponseError.setText(mContext.getString(R.string.stop_info_old_data,
                                                    relativeTime));
            mResponseError.setVisibility(View.VISIBLE);
        } else {
            mResponseError.setVisibility(View.GONE);
        }
    }

    void beginNameEdit(String initial) {
        // If we can click on this, then we're definitely not
        // editable, so we should go into edit mode.
        mEditNameView.setText((initial != null) ? initial : mNameView.getText());
        mNameContainerView.setVisibility(View.GONE);
        mDirectionView.setVisibility(View.GONE);
        mFilterGroup.setVisibility(View.GONE);
        mEditNameContainerView.setVisibility(View.VISIBLE);
        mEditNameView.requestFocus();
        mInNameEdit = true;
        // TODO: Ensure the soft keyboard is up
    }

    void endNameEdit() {
        mInNameEdit = false;
        mNameContainerView.setVisibility(View.VISIBLE);
        mEditNameContainerView.setVisibility(View.GONE);
        mDirectionView.setVisibility(View.VISIBLE);
        //setFilterHeader();
        InputMethodManager imm =
                (InputMethodManager)mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mEditNameView.getWindowToken(), 0);
        refreshName();
    }
}
