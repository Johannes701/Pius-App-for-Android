package com.rmkrings.main.pius_app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.rmkrings.data.adapter.CalendarDateListAdapter;
import com.rmkrings.data.adapter.CalendarMonthListAdapter;
import com.rmkrings.data.calendar.Calendar;
import com.rmkrings.data.calendar.DayItem;
import com.rmkrings.data.calendar.MonthItem;
import com.rmkrings.interfaces.ViewSelectedCallback;
import com.rmkrings.helper.Cache;
import com.rmkrings.interfaces.HttpResponseCallback;
import com.rmkrings.http.HttpResponseData;
import com.rmkrings.loader.CalendarLoader;
import com.rmkrings.pius_app_for_android.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;
import java.util.logging.Logger;

/**
 */
public class CalendarFragment extends Fragment implements HttpResponseCallback, ViewSelectedCallback {

    // Outlets
    private ProgressBar mProgressBar;
    private CalendarMonthListAdapter mCalendarMonthListAdapter;
    private CalendarDateListAdapter mCalendarDateListAdapter;
    private Button mSelectedButton = null;

    // Local State
    private final String digestFileName = "calendar.md5";
    private final String cacheFileName = "calendar.json";

    private final Cache cache = new Cache();
    private Calendar calendar;
    private final ArrayList<String> monthList = new ArrayList<>();
    private final ArrayList<DayItem> dateList = new ArrayList<>();
    private FragmentActivity fragmentActivity;

    private final static Logger logger = Logger.getLogger(CalendarLoader.class.getName());

    public CalendarFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Outlets
        mProgressBar = view.findViewById(R.id.progressBar);
        RecyclerView mMonthList = view.findViewById(R.id.monthlist);
        RecyclerView mDateList = view.findViewById(R.id.datelist);
        RecyclerView.LayoutManager mHorizontalLayoutManager = new LinearLayoutManager(PiusApplication.getAppContext(), LinearLayoutManager.HORIZONTAL, false);
        RecyclerView.LayoutManager mVerticalLayoutManager = new LinearLayoutManager(PiusApplication.getAppContext(), LinearLayoutManager.VERTICAL, false);

        mMonthList.setHasFixedSize(true);
        mMonthList.setLayoutManager(mHorizontalLayoutManager);
        mCalendarMonthListAdapter = new CalendarMonthListAdapter(monthList, this);
        mMonthList.setAdapter(mCalendarMonthListAdapter);

        mDateList.setLayoutManager(mVerticalLayoutManager);
        mDateList.addItemDecoration(new DividerItemDecoration(mDateList.getContext(), DividerItemDecoration.VERTICAL));
        mCalendarDateListAdapter = new CalendarDateListAdapter(dateList);
        mDateList.setAdapter(mCalendarDateListAdapter);

        ImageButton mSearchButton = view.findViewById(R.id.searchbutton);
        mSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction transaction = fragmentActivity.getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.frameLayout, CalendarSearchFragment.newInstance(calendar));
                transaction.addToBackStack(null);
                transaction.commit();
            }
        });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        fragmentActivity = (FragmentActivity)context;
    }

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getActivity()).setTitle(R.string.title_calendar);
        BottomNavigationView mNavigationView = getActivity().findViewById(R.id.navigation);
        mNavigationView.getMenu().getItem(3).setChecked(true);

        reload();
    }

    private void setMonthList() {
        monthList.clear();
        for (MonthItem monthItem: calendar.getMonthItems()) {
            monthList.add(monthItem.getName());
        }

        mCalendarMonthListAdapter.notifyDataSetChanged();
    }

    private void setDateList(String monthName) {
        dateList.clear();
        MonthItem monthItem = calendar.getMonthItem(monthName);
        if (monthItem != null) {
            dateList.addAll(monthItem.getDayItems());
        }

        mCalendarDateListAdapter.notifyDataSetChanged();
    }

    private void reload() {
        String digest;

        if (cache.fileExists(cacheFileName) && cache.fileExists(digestFileName)) {
            digest = cache.read(digestFileName);
        } else {
            logger.info(String.format("Cache and/or digest file %s does not exist. Not sending digest.", cacheFileName));
            digest = null;
        }

        CalendarLoader calendarLoader = new CalendarLoader();
        calendarLoader.load(this, digest);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void execute(HttpResponseData responseData) {
        String data;
        JSONObject jsonData;

        mProgressBar.setVisibility(View.INVISIBLE);

        if (responseData.getHttpStatusCode() != null && responseData.getHttpStatusCode() != 200 && responseData.getHttpStatusCode() != 304) {
            logger.severe(String.format("Failed to load data for Calendar. HTTP Status code %d.", responseData.getHttpStatusCode()));
            new AlertDialog.Builder(Objects.requireNonNull(getContext()), R.style.AlertDialogTheme)
                    .setTitle(getResources().getString(R.string.title_calendar))
                    .setMessage(getResources().getString(R.string.error_failed_to_load_data))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (getFragmentManager() != null) {
                                getFragmentManager().popBackStack();
                            }
                        }
                    })
                    .show();
            return;
        }

        if (responseData.getData() != null) {
            data = responseData.getData();
            cache.store(cacheFileName, data);
        } else {
            data = cache.read(cacheFileName);
        }

        try {
            jsonData = new JSONObject(data);
            calendar = new Calendar(jsonData);

            if (responseData.getHttpStatusCode() != null && responseData.getHttpStatusCode() != 304 && calendar.getDigest() != null) {
                cache.store(digestFileName, calendar.getDigest());
            }

            setMonthList();
        } catch (Exception e) {
            e.printStackTrace();
            new AlertDialog.Builder(Objects.requireNonNull(getContext()), R.style.AlertDialogTheme)
                    .setTitle(getResources().getString(R.string.title_calendar))
                    .setMessage(getResources().getString(R.string.error_failed_to_load_data))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (getFragmentManager() != null) {
                                getFragmentManager().popBackStack();
                            }
                        }
                    })
                    .show();
        }
    }

    @Override
    public void notifySelectionChanged(View b, String title) {
        if (mSelectedButton != null) {
            mSelectedButton.setSelected(false);
        }

        b.setSelected(true);
        mSelectedButton = (Button)b;

        setDateList(title);
    }
}
