/*
* Copyright (C) 2014-2015 University of South Florida (sjbarbeau@gmail.com)
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.onebusaway.android.report.ui;

import org.onebusaway.android.R;
import org.onebusaway.android.io.ObaAnalytics;
import org.onebusaway.android.io.elements.ObaArrivalInfo;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.io.elements.ObaTripStatus;
import org.onebusaway.android.report.connection.ServiceDescriptionTask;
import org.onebusaway.android.report.connection.ServiceRequestTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.model.AttributeValue;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;
import org.onebusaway.android.report.ui.util.ServiceUtils;
import org.onebusaway.android.util.MyTextUtils;
import org.onebusaway.android.util.PreferenceUtils;
import org.onebusaway.android.util.UIUtils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.PopupMenu;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import edu.usf.cutr.open311client.Open311;
import edu.usf.cutr.open311client.constants.Open311DataType;
import edu.usf.cutr.open311client.models.Open311Attribute;
import edu.usf.cutr.open311client.models.Open311AttributePair;
import edu.usf.cutr.open311client.models.Open311User;
import edu.usf.cutr.open311client.models.Service;
import edu.usf.cutr.open311client.models.ServiceDescription;
import edu.usf.cutr.open311client.models.ServiceDescriptionRequest;
import edu.usf.cutr.open311client.models.ServiceRequest;
import edu.usf.cutr.open311client.models.ServiceRequestResponse;
import edu.usf.cutr.open311client.utils.Open311Validator;

public class Open311ProblemFragment extends BaseReportFragment implements
        ServiceDescriptionTask.Callback, ServiceRequestTask.Callback {

    private ImageView mIssueImage;

    private Open311 mOpen311;

    private Service mService;

    private String mAgencyName;

    // Arrival information for trip problem
    private ObaArrivalInfo mArrivalInfo;

    // Captured image url
    private Uri mCapturedImageURI;

    // Open311 service description result for selected service code
    private ServiceDescription mServiceDescription;

    // Store ServiceDescription Task Result if host activity haven't been created
    private ServiceDescription mServiceDescriptionTaskResult;

    // Load dynamic open311 fields into info layout
    private LinearLayout mInfoLayout;

    private CheckBox mAnonymousReportingCheckBox;

    private EditText mContactNameView;

    private EditText mContactLastNameView;

    private EditText mContactEmailView;

    private EditText mContactPhoneView;

    private ProgressDialog mProgressDialog;

    private boolean mIsProgressDialogShowing = false;

    private Map<Integer, AttributeValue> mAttributeValueHashMap = new HashMap<>();

    private Map<Integer, View> mDynamicAttributeUIMap = new HashMap<>();

    // Maps attribute name + id with its key
    private Map<String, String> mOpen311AttributeKeyNameMap = new HashMap<>();

    private ServiceRequestTask mRequestTask;

    private ReportProblemFragmentCallback mCallback;

    public static final String TAG = "Open311ProblemFragment";

    private static final String ATTRIBUTES = ".attributes";

    private static final String IMAGE = ".image";

    private static final String IMAGE_URI = ".imageUri";

    private static final String TRIP_INFO = ".tripInfo";

    private static final String SHOW_PROGRESS_DIALOG = ".showProgressDialog";

    private static final String AGENCY_NAME = ".agencyName";

    public static void show(AppCompatActivity activity, Integer containerViewId,
                            Open311 open311, Service service, ObaArrivalInfo obaArrivalInfo,
                            String agencyName) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Open311ProblemFragment fragment = new Open311ProblemFragment();
        fragment.setOpen311(open311);
        fragment.setService(service);
        fragment.setArrivalInfo(obaArrivalInfo);
        fragment.setAgencyName(agencyName);

        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(containerViewId, fragment, TAG);
        ft.addToBackStack(null);
        ft.commit();
    }

    public static void show(AppCompatActivity activity, Integer containerViewId,
                            Open311 open311, Service service) {
        Open311ProblemFragment.show(activity, containerViewId, open311, service, null, null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.open311_issue, container, false);

        setRetainInstance(true);

        setHasOptionsMenu(Boolean.TRUE);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupViews(savedInstanceState);

        setupIconColors();

        setUpContactInfoViews();

        callServiceDescription();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        List<AttributeValue> attributeValues = createAttributeValues(mServiceDescription);
        if (attributeValues.size() > 0) {
            outState.putParcelableArrayList(ATTRIBUTES, (ArrayList<? extends Parcelable>) attributeValues);
        }

        if (mCapturedImageURI != null) {
            Bitmap bitmap = ((BitmapDrawable) mIssueImage.getDrawable()).getBitmap();
            outState.putParcelable(IMAGE, bitmap);
            outState.putParcelable(IMAGE_URI, mCapturedImageURI);
        }

        if (mArrivalInfo != null) {
            outState.putSerializable(TRIP_INFO, mArrivalInfo);
        }
        if (mIsProgressDialogShowing) {
            // Dismiss the progress dialog when orientation change to prevent leaked window
            mProgressDialog.dismiss();
        }
        outState.putBoolean(SHOW_PROGRESS_DIALOG, mIsProgressDialogShowing);
        outState.putString(AGENCY_NAME, mAgencyName);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            mCapturedImageURI = savedInstanceState.getParcelable(IMAGE_URI);

            mArrivalInfo = (ObaArrivalInfo) savedInstanceState.getSerializable(TRIP_INFO);
            mAgencyName = savedInstanceState.getString(AGENCY_NAME);

            List<AttributeValue> values = savedInstanceState.getParcelableArrayList(ATTRIBUTES);
            mAttributeValueHashMap.clear();
            if (values != null) {
                for (AttributeValue v : values) {
                    mAttributeValueHashMap.put(v.getCode(), v);
                }
            }

            mIsProgressDialogShowing = savedInstanceState.getBoolean(SHOW_PROGRESS_DIALOG);
            if (mIsProgressDialogShowing) {
                showProgressDialog(true);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (mServiceDescriptionTaskResult != null) {
            // Reload service description task if the activity restored
            this.onServiceDescriptionTaskCompleted(mServiceDescriptionTaskResult);
            mServiceDescriptionTaskResult = null;
        }

        try {
            mCallback = (ReportProblemFragmentCallback) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("ReportProblemFragmentCallback should be implemented" +
                    " in parent activity");
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    /**
     * Initialize UI components
     */
    private void setupViews(Bundle bundle) {
        mIssueImage = (ImageView) findViewById(R.id.ri_imageView);
        if (bundle != null && bundle.getParcelable(IMAGE) != null) {
            mIssueImage.setImageBitmap((Bitmap) bundle.getParcelable(IMAGE));
        }

        mInfoLayout = (LinearLayout) findViewById(R.id.ri_info_layout);

        Button addImageButton = (Button) findViewById(R.id.ri_attach_image);

        final PopupMenu popupMenu = new PopupMenu(getActivity(), addImageButton);
        popupMenu.inflate(R.menu.report_issue_add_image);
        addImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                popupMenu.show();
            }
        });

        popupMenu.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.ri_button_camera:
                                openCamera();
                                break;
                            case R.id.ri_button_gallery:
                                openGallery();
                                break;
                            default:
                                break;
                        }
                        return true;
                    }
                });

        mAnonymousReportingCheckBox = (CheckBox) findViewById(R.id.rici_anonymous_checkbox);
        mAnonymousReportingCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                disableEnableContactInfoViews(isChecked);
            }
        });

        // Setup contact info views
        mContactNameView = ((EditText) findViewById(R.id.rici_name_editText));
        mContactLastNameView = ((EditText) findViewById(R.id.rici_lastname_editText));
        mContactEmailView = ((EditText) findViewById(R.id.rici_email_editText));
        mContactPhoneView = ((EditText) findViewById(R.id.rici_phone_editText));
    }

    private void setupIconColors() {
        ((ImageView) findViewById(R.id.ri_ic_app_feedback)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_ic_image_picker)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_ic_username)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_ic_customer_service_email)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_ic_customer_service_phone)).setColorFilter(
                getResources().getColor(R.color.material_gray));
        ((ImageView) findViewById(R.id.ri_ic_anonymous)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    private void callServiceDescription() {
        showProgress(Boolean.TRUE);
        Location location = getIssueLocationHelper().getIssueLocation();
        ServiceDescriptionRequest sdr = new ServiceDescriptionRequest(location.getLatitude(),
                location.getLongitude(), mOpen311.getJurisdiction(), mService.getService_code());

        ServiceDescriptionTask sdt = new ServiceDescriptionTask(sdr, mOpen311,
                Open311ProblemFragment.this);
        sdt.execute();
    }

    /**
     * Update ui if service description request is success
     */
    @Override
    public void onServiceDescriptionTaskCompleted(ServiceDescription serviceDescription) {
        if (isActivityAttached()) {
            showProgress(Boolean.FALSE);
            if (serviceDescription.isSuccess()) {
                createServiceDescriptionUI(serviceDescription);
            } else {
                createToastMessage(getString(R.string.ri_service_description_problem));
                // Close open311 fragment
                ((InfrastructureIssueActivity) getActivity()).removeOpen311ProblemFragment();
            }
        } else {
            mServiceDescriptionTaskResult = serviceDescription;
        }
    }

    /**
     * Show the result of the open311 issue submission
     */
    @Override
    public void onServiceRequestTaskCompleted(ServiceRequestResponse response) {
        showProgressDialog(false);

        if (response.isSuccess()) {
            mCallback.onSendReport();
        } else {
            String message = response.getErrorMessage();
            if (TextUtils.isEmpty(message)) {
                message = getString(R.string.ri_unsuccessful_submit);
            }
            createToastMessage(message);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.report_issue_action, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.report_problem_send) {
            submitReport();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ReportConstants.GALLERY_INTENT && resultCode == Activity.RESULT_OK &&
                data != null) {
            mCapturedImageURI = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getActivity().getContentResolver().query(mCapturedImageURI,
                    filePathColumn, null, null, null);
            if (cursor == null) return;
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            mIssueImage.setImageBitmap(BitmapFactory.decodeFile(picturePath));
        } else if (requestCode == ReportConstants.CAPTURE_PICTURE_INTENT &&
                resultCode == Activity.RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            mIssueImage.setImageBitmap(imageBitmap);
        }
    }

    /**
     * Prepare submit forms and submit report
     */
    private void submitReport() {
        // Save the open311 user
        saveOpen311User();

        // Prepare issue description
        String description = ((EditText) getActivity().findViewById(R.id.ri_editTextDesc)).getText().toString();

        final TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        Open311User open311User;

        if (!mAnonymousReportingCheckBox.isChecked()) {
            open311User = getOpen311UserFromUI();
        } else {
            open311User = getOpen311UserFromStrings();
        }

        IssueLocationHelper issueLocationHelper = getIssueLocationHelper();

        ServiceRequest.Builder builder = new ServiceRequest.Builder();
        builder.setJurisdiction_id(mOpen311.getJurisdiction()).setService_code(mService.getService_code()).
                setService_name(mService.getService_name()).
                setLatitude(issueLocationHelper.getIssueLocation().getLatitude()).
                setLongitude(issueLocationHelper.getIssueLocation().getLongitude()).setSummary(null).
                setDescription(description).setEmail(open311User.getEmail()).
                setFirst_name(open311User.getName()).setLast_name(open311User.getLastName()).
                setPhone(open311User.getPhone()).setAddress_string(getCurrentAddress()).
                setDevice_id(tm.getDeviceId());

        if (mCapturedImageURI != null) {
            try {
                builder.setMedia(createImageFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ServiceRequest serviceRequest = builder.createServiceRequest();
        List<Open311AttributePair> attributes = createOpen311Attributes(mServiceDescription);
        serviceRequest.setAttributes(attributes);

        int errorCode = Open311Validator.validateServiceRequest(serviceRequest,
                mOpen311.getOpen311Option().getOpen311Type(), mServiceDescription);


        if (Open311Validator.isValid(errorCode)) {
            // Append transit service parameters to issue description
            if (ServiceUtils.isTransitServiceByType(mService.getType())) {
                description += getTransitIssueParameters(mService);
                serviceRequest.setDescription(description);
            }

            //Start progress
            showProgressDialog(true);

            mRequestTask = new ServiceRequestTask(mOpen311, serviceRequest, this);
            mRequestTask.execute();

            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.SUBMIT.toString(),
                    getString(R.string.analytics_action_problem), mService.getService_name());
        } else {
            createToastMessage(Open311Validator.getErrorMessageForServiceRequestByErrorCode(errorCode));
        }
    }

    /**
     * Generates stop and trip problem parameters for given open311 service
     *
     * @param service open311 service
     * @return a string containing parameters
     */
    private String getTransitIssueParameters(Service service) {
        StringBuilder sb = new StringBuilder();
        ObaStop obaStop = getIssueLocationHelper().getObaStop();
        if (obaStop == null) {
            return sb.toString();
        }

        sb.append(getString(R.string.ri_append_start));

        if (ServiceUtils.isTransitStopServiceByType(service.getType())) {
            // Append stop service params
            sb.append(getResources().getString(R.string.ri_append_gtfs_stop_id, obaStop.getId()));
        } else if (ServiceUtils.isTransitTripServiceByType(service.getType())) {
            if (mArrivalInfo != null) {
                // Append trip service params
                DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy");

                sb.append(getResources().getString(R.string.ri_append_service_date,
                        dateFormat.format(new Date(mArrivalInfo.getServiceDate()))));
                sb.append(getResources().getString(R.string.ri_append_agency_name, mAgencyName));
                sb.append(getResources().getString(R.string.ri_append_gtfs_stop_id, obaStop.getId()));

                sb.append(getResources().getString(R.string.ri_append_route_id, mArrivalInfo.getRouteId()));
                String routeDisplayName = UIUtils.getRouteDisplayName(mArrivalInfo);
                if (!TextUtils.isEmpty(routeDisplayName)) {
                    sb.append(getResources().getString(R.string.ri_append_route_display_name,
                            routeDisplayName));
                }

                sb.append(getResources().getString(R.string.ri_append_trip_id, mArrivalInfo.getTripId()));
                sb.append(getResources().getString(R.string.ri_append_trip_name, mArrivalInfo.getHeadsign()));

                sb.append(getResources().getString(R.string.ri_append_predicted,
                        Boolean.valueOf(mArrivalInfo.getPredicted())));

                ObaTripStatus tripStatus = mArrivalInfo.getTripStatus();
                if (tripStatus != null && mArrivalInfo.getPredicted()) {
                    sb.append(getResources().getString(R.string.ri_append_vehicle_id, mArrivalInfo.getVehicleId()));

                    Location lastKnownLocation = tripStatus.getLastKnownLocation();
                    if (lastKnownLocation != null) {
                        String locationString = lastKnownLocation.getLatitude() +
                                " " + lastKnownLocation.getLongitude();
                        sb.append(getResources().getString(R.string.ri_append_vehicle_location,
                                locationString));
                    }

                    DecimalFormat numberFormat = new DecimalFormat("#.000");
                    double scheduleDeviation = tripStatus.getScheduleDeviation() / 60.0;
                    if (scheduleDeviation == 0.0) {
                        sb.append(getResources().getString(R.string.ri_append_schedule_deviation,
                                "0"));
                    } else if (scheduleDeviation < 0) {
                        sb.append(getResources().getString(R.string.ri_append_schedule_deviation_early,
                                numberFormat.format(scheduleDeviation * -1.0)));
                    } else if (scheduleDeviation > 0) {
                        sb.append(getResources().getString(R.string.ri_append_schedule_deviation_late,
                                numberFormat.format(scheduleDeviation)));
                    }
                }

                dateFormat = new SimpleDateFormat("hh:mm a");
                if (mArrivalInfo.getPredicted()) {
                    sb.append(getResources().getString(R.string.ri_append_arrival_time,
                            dateFormat.format(new Date(mArrivalInfo.getPredictedArrivalTime()))));
                    sb.append(getResources().getString(R.string.ri_append_departure_time,
                            dateFormat.format(new Date(mArrivalInfo.getPredictedDepartureTime()))));
                } else {
                    sb.append(getResources().getString(R.string.ri_append_arrival_time,
                            dateFormat.format(new Date(mArrivalInfo.getScheduledArrivalTime()))));
                    sb.append(getResources().getString(R.string.ri_append_departure_time,
                            dateFormat.format(new Date(mArrivalInfo.getScheduledDepartureTime()))));
                }
            }

        }

        return sb.toString();
    }

    /**
     * Creates open311 question and answer attributes to submit a report
     * Reads from dynamically created UI
     *
     * @param serviceDescription contains attribute types
     * @return List of code value pair of attributes
     */
    private List<Open311AttributePair> createOpen311Attributes(ServiceDescription serviceDescription) {
        List<Open311AttributePair> attributes = new ArrayList<>();

        for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {

            if (Boolean.valueOf(open311Attribute.getVariable())) {
                if (Open311DataType.STRING.equals(open311Attribute.getDatatype())
                        || Open311DataType.NUMBER.equals(open311Attribute.getDatatype())
                        || Open311DataType.DATETIME.equals(open311Attribute.getDatatype())) {
                    EditText et = (EditText) mDynamicAttributeUIMap.get(open311Attribute.getCode());
                    if (et != null) {
                        attributes.add(new Open311AttributePair(open311Attribute.getCode(),
                                et.getText().toString(), open311Attribute.getDatatype()));
                    }
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getDatatype())) {
                    RadioGroup rg = (RadioGroup) mDynamicAttributeUIMap.get(open311Attribute.getCode());
                    if (rg != null) {
                        int count = rg.getChildCount();
                        for (int i = 0; i < count; i++) {
                            RadioButton rb = (RadioButton) rg.getChildAt(i);
                            if (rb.isChecked()) {
                                String attributeKey = mOpen311AttributeKeyNameMap.get(
                                        open311Attribute.getCode() + rb.getText().toString());
                                attributes.add(new Open311AttributePair(open311Attribute.getCode(),
                                        attributeKey, open311Attribute.getDatatype()));
                                break;
                            }
                        }
                    }
                } else if (Open311DataType.MULTIVALUELIST.equals(open311Attribute.getDatatype())) {
                    LinearLayout ll = (LinearLayout) mDynamicAttributeUIMap.get(open311Attribute.getCode());
                    if (ll != null) {
                        int count = ll.getChildCount();
                        for (int i = 0; i < count; i++) {
                            CheckBox cb = (CheckBox) ll.getChildAt(i);
                            if (cb.isChecked()) {
                                String attributeKey = mOpen311AttributeKeyNameMap.get(
                                        open311Attribute.getCode() + cb.getText().toString());
                                attributes.add(new Open311AttributePair(open311Attribute.getCode(),
                                        attributeKey, open311Attribute.getDatatype()));
                            }
                        }
                    }
                }
            }
        }
        return attributes;
    }

    /**
     * This method dynamically reads all user inputted the values from the screen and puts into a
     * list
     *
     * @param serviceDescription displayed service description
     * @return List of attribute values
     */
    private List<AttributeValue> createAttributeValues(ServiceDescription serviceDescription) {
        List<AttributeValue> values = new ArrayList<>();
        for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {
            if (Boolean.valueOf(open311Attribute.getVariable())) {
                if (Open311DataType.STRING.equals(open311Attribute.getDatatype())
                        || Open311DataType.NUMBER.equals(open311Attribute.getDatatype())
                        || Open311DataType.DATETIME.equals(open311Attribute.getDatatype())) {
                    EditText et = (EditText) mDynamicAttributeUIMap.get(open311Attribute.getCode());
                    if (et != null) {
                        AttributeValue value = new AttributeValue(open311Attribute.getCode());
                        value.addValue(et.getText().toString());
                        values.add(value);
                    }
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getDatatype())) {
                    RadioGroup rg = (RadioGroup) mDynamicAttributeUIMap.get(open311Attribute.getCode());
                    if (rg != null) {
                        int count = rg.getChildCount();
                        for (int i = 0; i < count; i++) {
                            RadioButton rb = (RadioButton) rg.getChildAt(i);
                            if (rb.isChecked()) {
                                AttributeValue value = new AttributeValue(open311Attribute.getCode());
                                value.addValue(rb.getText().toString());
                                values.add(value);
                                break;
                            }
                        }
                    }
                } else if (Open311DataType.MULTIVALUELIST.equals(open311Attribute.getDatatype())) {
                    LinearLayout ll = (LinearLayout) mDynamicAttributeUIMap.get(open311Attribute.getCode());
                    if (ll != null) {
                        int count = ll.getChildCount();
                        AttributeValue value = new AttributeValue(open311Attribute.getCode());
                        for (int i = 0; i < count; i++) {
                            CheckBox cb = (CheckBox) ll.getChildAt(i);
                            if (cb.isChecked()) {
                                value.addValue(cb.getText().toString());
                            }
                        }
                        if (value.getValues().size() > 0)
                            values.add(value);
                    }
                }
            }
        }
        return values;
    }

    /**
     * Creates a byte array which contains the image data
     *
     * @return image in bytes
     */
    private File createImageFile() throws IOException {
        //Convert bitmap to file
        Bitmap bitmap = ((BitmapDrawable) mIssueImage.getDrawable()).getBitmap();
        File filesDir = getActivity().getFilesDir();
        File imageFile = new File(filesDir, "upload.jpg");

        OutputStream os;
        os = new FileOutputStream(imageFile);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os);
        os.flush();
        os.close();
        return imageFile;
    }

    private void openCamera() {
        String fileName = "temp.jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, fileName);
        mCapturedImageURI = getActivity().getContentResolver().insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, ReportConstants.CAPTURE_PICTURE_INTENT);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, ReportConstants.GALLERY_INTENT);
    }

    /**
     * Dynamically creates Open311 questions from service description
     *
     * @param serviceDescription contains Open311 questions
     */
    public void createServiceDescriptionUI(ServiceDescription serviceDescription) {
        clearInfoField();
        this.mServiceDescription = serviceDescription;
        if (!"".equals(mService.getDescription()) && mService.getDescription() != null) {
            addDescriptionText(mService.getDescription());
        }

        if (mArrivalInfo != null && ServiceUtils.isTransitTripServiceByType(mService.getType())) {
            createTripHeadsign(mArrivalInfo.getHeadsign());
        }

        for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {
            if (!Boolean.valueOf(open311Attribute.getVariable())) {
                addDescriptionText(open311Attribute.getDescription());
            } else {
                if (Open311DataType.STRING.equals(open311Attribute.getDatatype())
                        || Open311DataType.NUMBER.equals(open311Attribute.getDatatype())
                        || Open311DataType.DATETIME.equals(open311Attribute.getDatatype())) {
                    createEditText(open311Attribute);
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getDatatype())) {
                    createSingleValueList(open311Attribute);
                } else if (Open311DataType.MULTIVALUELIST.equals(open311Attribute.getDatatype())) {
                    createMultiValueList(open311Attribute);
                }
            }
        }
    }

    /**
     * Dynamically creates an edit text
     *
     * @param open311Attribute contains the open311 attributes
     */
    private void createEditText(Open311Attribute open311Attribute) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_text_item, null, false);

        ImageView icon = ((ImageView) layout.findViewById(R.id.ri_ic_question_answer));
        icon.setColorFilter(getResources().getColor(R.color.material_gray));

        Spannable desc = new SpannableString(MyTextUtils.toSentenceCase(open311Attribute.getDescription()));
        EditText editText = ((EditText) layout.findViewById(R.id.riti_editText));
        if (open311Attribute.getRequired()) {
            Spannable req = new SpannableString("(required)");
            req.setSpan(new ForegroundColorSpan(Color.RED), 0, req.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            editText.setHint(TextUtils.concat(desc, " ", req));
        } else {
            editText.setHint(desc);
        }

        if (Open311DataType.NUMBER.equals(open311Attribute.getDatatype())) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if (Open311DataType.DATETIME.equals(open311Attribute.getDatatype())) {
            editText.setInputType(InputType.TYPE_CLASS_DATETIME);
        }

        // Restore view state from attribute result hash map
        AttributeValue av = mAttributeValueHashMap.get(open311Attribute.getCode());
        if (av != null) {
            editText.setText(av.getSingleValue());
        }

        // Dynamically fill stop id if this is a transit service
        // And if this is a bus stop field
        if (ServiceUtils.isTransitServiceByType(mService.getType())
                && ServiceUtils.isStopIdField(open311Attribute.getDescription())) {

            icon.setImageDrawable(ContextCompat.getDrawable(getActivity(),
                    R.drawable.ri_flag_triangle));

            ObaStop obaStop = getIssueLocationHelper().getObaStop();
            if (obaStop != null) {
                editText.setText(obaStop.getStopCode());
            }
        }

        mInfoLayout.addView(layout);
        mDynamicAttributeUIMap.put(open311Attribute.getCode(), editText);
    }

    /**
     * Dynamically creates radio buttons
     *
     * @param open311Attribute contains the open311 attributes
     */
    private void createSingleValueList(Open311Attribute open311Attribute) {
        ArrayList<Object> values = (ArrayList<Object>) open311Attribute.getValues();
        if (values != null && values.size() > 0) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            RelativeLayout layout = (RelativeLayout) inflater.inflate(
                    R.layout.report_issue_single_value_list_item, null, false);
            layout.setSaveEnabled(true);
            ((ImageView) layout.findViewById(R.id.ri_ic_radio)).setColorFilter(
                    getResources().getColor(R.color.material_gray));

            Spannable word = new SpannableString(open311Attribute.getDescription());
            ((TextView) layout.findViewById(R.id.risvli_textView)).setText(word);

            if (open311Attribute.getRequired()) {
                Spannable wordTwo = new SpannableString(" *Required");
                wordTwo.setSpan(new ForegroundColorSpan(Color.RED), 0, wordTwo.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ((TextView) layout.findViewById(R.id.risvli_textView)).append(wordTwo);
            }

            RadioGroup rg = (RadioGroup) layout.findViewById(R.id.risvli_radioGroup);
            rg.setOrientation(RadioGroup.VERTICAL);

            // Restore view state from attribute result hash map
            AttributeValue av = mAttributeValueHashMap.get(open311Attribute.getCode());
            String entryValue = null;
            if (av != null) {
                entryValue = av.getSingleValue();
            }

            for (int i = 0; i < values.size(); i++) {
                LinkedHashMap<String, String> value = (LinkedHashMap<String, String>) values.get(i);
                RadioButton rb = new RadioButton(getActivity());
                rg.addView(rb); //the RadioButtons are added to the radioGroup instead of the layout
                String attributeKey = "";
                String attributeValue = "";
                for (LinkedHashMap.Entry<String, String> entry : value.entrySet()) {
                    if (Open311Attribute.NAME.equals(entry.getKey())) {
                        rb.setText(entry.getValue());
                        if (entryValue != null && entryValue.equalsIgnoreCase(entry.getValue())) {
                            rb.setChecked(true);
                        }
                        attributeKey = open311Attribute.getCode() + entry.getValue();
                    } else if (Open311Attribute.KEY.equals(entry.getKey())) {
                        attributeValue = entry.getValue();
                    }
                }
                mOpen311AttributeKeyNameMap.put(attributeKey, attributeValue);
            }

            mInfoLayout.addView(layout);
            mDynamicAttributeUIMap.put(open311Attribute.getCode(), rg);
        }
    }

    /**
     * Dynamically creates checkboxes
     *
     * @param open311Attribute contains the open311 attributes
     */
    private void createMultiValueList(Open311Attribute open311Attribute) {
        ArrayList<Object> values = (ArrayList<Object>) open311Attribute.getValues();
        if (values != null && values.size() > 0) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            RelativeLayout layout = (RelativeLayout) inflater.inflate(
                    R.layout.report_issue_multi_value_list_item, null, false);

            ((ImageView) layout.findViewById(R.id.ri_ic_checkbox)).setColorFilter(
                    getResources().getColor(R.color.material_gray));

            Spannable word = new SpannableString(open311Attribute.getDescription());
            ((TextView) layout.findViewById(R.id.rimvli_textView)).setText(word);

            if (open311Attribute.getRequired()) {
                Spannable wordTwo = new SpannableString(" *Required");
                wordTwo.setSpan(new ForegroundColorSpan(Color.RED), 0, wordTwo.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ((TextView) layout.findViewById(R.id.rimvli_textView)).append(wordTwo);
            }

            // Restore view state from attribute result hash map
            AttributeValue av = mAttributeValueHashMap.get(open311Attribute.getCode());

            LinearLayout cg = (LinearLayout) layout.findViewById(R.id.rimvli_checkBoxGroup);
            for (int i = 0; i < values.size(); i++) {
                LinkedHashMap<String, String> value = (LinkedHashMap<String, String>) values.get(i);
                CheckBox cb = new CheckBox(getActivity());
                cg.addView(cb);
                String attributeKey = "";
                String attributeValue = "";
                for (LinkedHashMap.Entry<String, String> entry : value.entrySet()) {
                    if (Open311Attribute.NAME.equals(entry.getKey())) {
                        cb.setText(entry.getValue());
                        if (av != null && av.getValues().contains(entry.getValue())) {
                            cb.setChecked(true);
                        }
                        attributeKey = open311Attribute.getCode() + entry.getValue();
                    } else if (Open311Attribute.KEY.equals(entry.getKey())) {
                        attributeValue = entry.getValue();
                    }
                }
                mOpen311AttributeKeyNameMap.put(attributeKey, attributeValue);
            }

            mInfoLayout.addView(layout);
            mDynamicAttributeUIMap.put(open311Attribute.getCode(), cg);
        }
    }

    private void createTripHeadsign(String text) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(
                R.layout.report_issue_description_item, null, false);

        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_report_stop_problem);
        TextView tv = ((TextView) layout.findViewById(R.id.riii_textView));
        tv.setText(MyTextUtils.toSentenceCase(text));
        tv.setTypeface(null, Typeface.NORMAL);

        linear.addView(layout, 0);

        ((ImageView) layout.findViewById(R.id.ic_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    private void clearInfoField() {
        mInfoLayout.removeAllViewsInLayout();
    }

    private void addDescriptionText(String text) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(
                R.layout.report_issue_description_item, null, false);

        ((TextView) layout.findViewById(R.id.riii_textView)).setText(text);
        mInfoLayout.addView(layout);

        ((ImageView) layout.findViewById(R.id.ic_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    /**
     * Set up USer contact information view
     */
    private void setUpContactInfoViews() {
        Open311User open311User = getOpen311UserFromSharedPref();
        if (open311User.getName() != null)
            mContactNameView.setText(open311User.getName());
        if (open311User.getLastName() != null)
            mContactLastNameView.setText(open311User.getLastName());
        if (open311User.getEmail() != null)
            mContactEmailView.setText(open311User.getEmail());
        if (open311User.getPhone() != null)
            mContactPhoneView.setText(open311User.getPhone());
    }

    /**
     * This method disables or enables editing for contact info fields
     *
     * @param isDisabled true if you want to disable contact info fields
     */
    private void disableEnableContactInfoViews(boolean isDisabled) {
        mContactNameView.setEnabled(!isDisabled);
        mContactLastNameView.setEnabled(!isDisabled);
        mContactEmailView.setEnabled(!isDisabled);
        mContactPhoneView.setEnabled(!isDisabled);
    }

    /**
     * Get open311 user from shared preferences
     *
     * @return Open311User
     */
    private Open311User getOpen311UserFromSharedPref() {
        return new Open311User(PreferenceUtils.getString(ReportConstants.PREF_NAME),
                PreferenceUtils.getString(ReportConstants.PREF_LAST_NAME),
                PreferenceUtils.getString(ReportConstants.PREF_EMAIL),
                PreferenceUtils.getString(ReportConstants.PREF_PHONE));
    }

    /**
     * Get open311 user from fields on the screen
     *
     * @return Open311User
     */
    private Open311User getOpen311UserFromUI() {
        String name = ((EditText) findViewById(R.id.rici_name_editText)).getText().toString();
        String lastName = ((EditText) findViewById(R.id.rici_lastname_editText)).getText().toString();
        String email = ((EditText) findViewById(R.id.rici_email_editText)).getText().toString();
        String phone = ((EditText) findViewById(R.id.rici_phone_editText)).getText().toString();

        return new Open311User(name, lastName, email, phone);
    }


    /**
     * Get open311 user from static strings for anonymous reporting
     *
     * @return Open311User
     */
    public Open311User getOpen311UserFromStrings() {
        String name = getString(R.string.ri_static_user_name);
        String lastName = getString(R.string.ri_static_user_last_name);
        String email = getString(R.string.ri_static_user_email);
        String phone = getString(R.string.ri_static_user_phone);

        return new Open311User(name, lastName, email, phone);
    }

    /**
     * Save open311 user to shared prefs
     */
    private void saveOpen311User() {
        String name = ((EditText) findViewById(R.id.rici_name_editText)).getText().toString();
        String lastName = ((EditText) findViewById(R.id.rici_lastname_editText)).getText().toString();
        String email = ((EditText) findViewById(R.id.rici_email_editText)).getText().toString();
        String phone = ((EditText) findViewById(R.id.rici_phone_editText)).getText().toString();

        PreferenceUtils.saveString(ReportConstants.PREF_NAME, name);
        PreferenceUtils.saveString(ReportConstants.PREF_LAST_NAME, lastName);
        PreferenceUtils.saveString(ReportConstants.PREF_EMAIL, email);
        PreferenceUtils.saveString(ReportConstants.PREF_PHONE, phone);
    }

    private IssueLocationHelper getIssueLocationHelper() {
        return ((InfrastructureIssueActivity) getActivity()).getIssueLocationHelper();
    }

    private String getCurrentAddress() {
        String address = ((InfrastructureIssueActivity) getActivity()).getCurrentAddress();
        if (TextUtils.isEmpty(address)) {
            return null;
        } else {
            return address;
        }
    }

    /**
     * Show a progress icon on the action bar
     *
     * @param visible show or hide the progress icon based
     */
    private void showProgress(Boolean visible) {
        InfrastructureIssueActivity activity = ((InfrastructureIssueActivity) getActivity());
        if (activity != null) {
            activity.showProgress(visible);
        }
    }

    /**
     * Show a progress dialog on the screen
     *
     * @param visible show or hide the progress icon based
     */
    private void showProgressDialog(boolean visible) {
        if (visible) {
            mIsProgressDialogShowing = true;
            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setMessage(getActivity().getString(R.string.ri_submitting_message));
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (mRequestTask != null) {
                                mRequestTask.cancel(true);
                            }
                            mIsProgressDialogShowing = false;
                            mProgressDialog.dismiss();
                        }
                    });
            mProgressDialog.show();
        } else if (mProgressDialog != null && !visible && mProgressDialog.isShowing()) {
            mIsProgressDialogShowing = false;
            mProgressDialog.dismiss();
        }
    }

    private boolean isActivityAttached() {
        return getActivity() != null;
    }

    public void setOpen311(Open311 open311) {
        mOpen311 = open311;
    }

    public void setService(Service service) {
        mService = service;
    }

    public void setArrivalInfo(ObaArrivalInfo arrivalInfo) {
        mArrivalInfo = arrivalInfo;
    }

    public void setAgencyName(String agencyName) {
        this.mAgencyName = agencyName;
    }
}
