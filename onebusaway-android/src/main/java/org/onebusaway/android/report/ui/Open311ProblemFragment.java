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

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.onebusaway.android.R;
import org.onebusaway.android.app.Application;
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.report.connection.ServiceDescriptionTask;
import org.onebusaway.android.report.connection.ServiceRequestTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.open311.Open311;
import org.onebusaway.android.report.open311.Open311Manager;
import org.onebusaway.android.report.open311.constants.Open311DataType;
import org.onebusaway.android.report.open311.models.Open311Attribute;
import org.onebusaway.android.report.open311.models.Open311AttributePair;
import org.onebusaway.android.report.open311.models.Open311User;
import org.onebusaway.android.report.open311.models.Service;
import org.onebusaway.android.report.open311.models.ServiceDescription;
import org.onebusaway.android.report.open311.models.ServiceRequest;
import org.onebusaway.android.report.open311.models.ServiceRequestResponse;
import org.onebusaway.android.report.open311.utils.Open311Validator;
import org.onebusaway.android.report.ui.dialog.ReportSuccessDialog;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by Cagri Cetin
 */
public class Open311ProblemFragment extends BaseReportFragment implements View.OnClickListener,
        ServiceDescriptionTask.Callback, ServiceRequestTask.Callback {

    /**
     * UI elements
     */
    private Spinner servicesSpinner;

    private ImageView issueImage;

    private Button cameraButton;

    private Button galleryButton;

    //Captured image url
    private Uri mCapturedImageURI;

    //Open311 service description result for selected service code
    private ServiceDescription serviceDescription;

    public static final String TAG = "Open311ProblemFragment";

    public static void show(ActionBarActivity activity, Integer containerViewId) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Open311ProblemFragment fragment = new Open311ProblemFragment();

        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(containerViewId, fragment, TAG);
        ft.addToBackStack(null);
        ft.commit();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.open311_issue, container, false);

        setRetainInstance(true);
        setHasOptionsMenu(Boolean.TRUE);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupViews();
    }

    /**
     * Initialize UI components
     */
    private void setupViews() {
        issueImage = (ImageView) findViewById(R.id.ri_imageView);

        cameraButton = (Button) findViewById(R.id.ri_CameraButton);
        cameraButton.setOnClickListener(this);

        galleryButton = (Button) findViewById(R.id.ri_GalleryButton);
        galleryButton.setOnClickListener(this);

        servicesSpinner = (Spinner) findViewById(R.id.ri_spinnerServices);
        servicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                Service service = (Service) servicesSpinner.getSelectedItem();
                if (service.getService_code() != null) {
                    showProgress(Boolean.TRUE);
                    ServiceDescriptionTask sdt = new ServiceDescriptionTask(
                            getIssueLocationHelper().getIssueLocation(), service,
                            Open311ProblemFragment.this);
                    sdt.execute();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
            }
        });
        ArrayAdapter<Service> adapter = new ArrayAdapter<Service>(getActivity(),
                android.support.v7.appcompat.R.layout.support_simple_spinner_dropdown_item,
                getServiceList());
        servicesSpinner.setAdapter(adapter);
    }

    @Override
    public void onClick(View v) {
        if (v == cameraButton) {
            openCamera();
        } else if (v == galleryButton) {
            openGallery();
        }
    }

    /**
     * Update ui if service description request is success
     */
    @Override
    public void onServiceDescriptionTaskCompleted(ServiceDescription serviceDescription) {
        showProgress(Boolean.FALSE);
        if (serviceDescription.isSuccess()) {
            createServiceDescriptionUI(serviceDescription);
        }
    }

    /**
     * Show the result of the open311 issue submission
     */
    @Override
    public void onServiceRequestTaskCompleted(ServiceRequestResponse response) {
        showProgress(Boolean.FALSE);
        if (response.isSuccess()) {
            (new ReportSuccessDialog()).show((getActivity()).getSupportFragmentManager(), ReportSuccessDialog.TAG);
        } else {
            createToastMessage(response.getErrorMessage());
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.report_issue_action, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final int id = item.getItemId();
        if (id == R.id.user_info) {
            createContactInfoFragment();
        } else if (id == R.id.report_problem_send) {
            submitReport();
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Prepare submit forms and submit report
     */
    private void submitReport() {
        //Get current Open311 service
        String jurisdictionId = Application.get().getCurrentRegion().getOpen311JurisdictionId();
        Open311 open311 = Open311Manager.getOpen311ByJurisdiction(jurisdictionId);

        Service service = (Service) servicesSpinner.getSelectedItem();

        //Prepare issue description
        String description = ((EditText) getActivity().findViewById(R.id.ri_editTextDesc)).getText().toString();
        description = description + getBusStopInfo();

        final TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        Open311User open311User = getOpen311User();

        IssueLocationHelper issueLocationHelper = getIssueLocationHelper();

        ServiceRequest.Builder builder = new ServiceRequest.Builder();
        builder.setJurisdiction_id(open311.getJurisdiction()).setService_code(service.getService_code()).
                setService_name(service.getService_name()).setApi_key(open311.getApiKey()).
                setLat(issueLocationHelper.getIssueLocation().getLatitude()).
                setLang(issueLocationHelper.getIssueLocation().getLongitude()).setSummary(null).
                setDescription(description).setEmail(open311User.getEmail()).
                setFirst_name(open311User.getName()).setLast_name(open311User.getLastName()).
                setPhone(open311User.getPhone()).setAddress_string(getCurrentAddress()).
                setDevice_id(tm.getDeviceId());

        if (mCapturedImageURI != null) {
            builder.setMedia_url(mCapturedImageURI.toString());
            try {
                builder.setMedia(createImageFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ServiceRequest serviceRequest = builder.createServiceRequest();
        int errorCode = Open311Validator.validateServiceRequest(serviceRequest, open311.getOpen311Option().getOpen311Type(), serviceDescription);

        List<Open311AttributePair> attributes = createOpen311Attributes(serviceDescription);
        serviceRequest.setAttributes(attributes);

        if (Open311Validator.isValid(errorCode)) {
            //Start progress
            showProgress(Boolean.TRUE);

            ServiceRequestTask srt = new ServiceRequestTask(open311, serviceRequest, this);
            srt.execute();
        } else {
            createToastMessage(Open311Validator.getErrorMessageForServiceRequestByErrorCode(errorCode));

            if (errorCode == Open311Validator.PROBLEM_CODE_USER_NAME || errorCode == Open311Validator.PROBLEM_CODE_USER_LASTNAME ||
                    errorCode == Open311Validator.PROBLEM_CODE_USER_EMAIL)
                createContactInfoFragment();
        }
    }

    /**
     * Creates current bus stop information for current selected bus stop
     * @return message for selected bus stop
     */
    private String getBusStopInfo() {
        ObaStop obaStop = getIssueLocationHelper().getObaStop();
        if (obaStop == null) {
            return "";
        } else {
            return getResources().getString(R.string.ri_append_stop_id, obaStop.getStopCode());
        }
    }

    /**
     * Creates open311 question and answer attributes
     * Reads from dynamically created UI
     * @param serviceDescription contains attribute types
     * @return List of code value pair of attributes
     */
    private List<Open311AttributePair> createOpen311Attributes(ServiceDescription serviceDescription) {
        List<Open311AttributePair> attributes = new ArrayList<Open311AttributePair>();

        for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {

            if (Boolean.valueOf(open311Attribute.getVariable())) {
                if (Open311DataType.STRING.equals(open311Attribute.getData_type())
                        || Open311DataType.NUMBER.equals(open311Attribute.getData_type())
                        || Open311DataType.DATETIME.equals(open311Attribute.getData_type())) {
                    EditText et = (EditText) findViewById(R.id.riti_editText);
                    if (et != null) {
                        attributes.add(new Open311AttributePair(open311Attribute.getCode(), et.getText().toString()));
                    }
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getData_type())) {
                    RadioGroup rg = (RadioGroup) findViewById(R.id.risvli_radioGroup);
                    if (rg != null) {
                        int count = rg.getChildCount();
                        for (int i = 0; i < count; i++) {
                            RadioButton rb = (RadioButton) rg.getChildAt(i);
                            if (rb.isChecked()) {
                                attributes.add(new Open311AttributePair(open311Attribute.getCode(), rb.getText().toString()));
                                break;
                            }
                        }
                    }
                } else if (Open311DataType.MULTIVALUELIST.equals(open311Attribute.getData_type())) {
                    LinearLayout ll = (LinearLayout) findViewById(R.id.rimvli_checkBoxGroup);
                    if (ll != null) {
                        int count = ll.getChildCount();
                        for (int i = 0; i < count; i++) {
                            CheckBox cb = (CheckBox) ll.getChildAt(i);
                            if (cb.isChecked()) {
                                attributes.add(new Open311AttributePair(open311Attribute.getCode(), cb.getText().toString()));
                            }
                        }
                    }
                }
            }
        }
        return attributes;
    }

    /**
     * Creates a byte array which contains the image data
     * @return image in bytes
     * @throws IOException
     */
    private byte[] createImageFile() throws IOException {
        File f = new File(getActivity().getCacheDir(), "image");
        f.createNewFile();

        //Convert bitmap to byte array
        Bitmap bitmap = ((BitmapDrawable) issueImage.getDrawable()).getBitmap();
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    private void openCamera() {
        String fileName = "temp.jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, fileName);
        mCapturedImageURI = getActivity().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);
        startActivityForResult(intent, ReportConstants.CAPTURE_PICTURE_INTENT);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, ReportConstants.GALLERY_INTENT);
    }

    /**
     * Dynamically creates Open311 questions from service description
     * @param serviceDescription contains Open311 questions
     */
    public void createServiceDescriptionUI(ServiceDescription serviceDescription) {
        clearInfoField();
        this.serviceDescription = serviceDescription;
        Service service = (Service) servicesSpinner.getSelectedItem();
        if (!"".equals(service.getDescription()) && service.getDescription() != null)
            addInfoText(service.getDescription());

        for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {
            if (!Boolean.valueOf(open311Attribute.getVariable())) {
                addInfoText(open311Attribute.getDescription());
            } else {
                if (Open311DataType.STRING.equals(open311Attribute.getData_type())
                        || Open311DataType.NUMBER.equals(open311Attribute.getData_type())
                        || Open311DataType.DATETIME.equals(open311Attribute.getData_type())) {
                    createEditText(open311Attribute);
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getData_type())) {
                    createSingleValueList(open311Attribute);
                } else if (Open311DataType.MULTIVALUELIST.equals(open311Attribute.getData_type())) {
                    createMultiValueList(open311Attribute);
                }
            }
        }
    }

    /**
     * Dyanmically creates an edit text
     * @param open311Attribute contains the open311 attributes
     */
    private void createEditText(Open311Attribute open311Attribute) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_text_item, null, false);


        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);
        Spannable word = new SpannableString(open311Attribute.getDescription());
        ((TextView) layout.findViewById(R.id.riti_textView)).setText(word);

        if (open311Attribute.getRequired()) {
            Spannable wordTwo = new SpannableString(" *Required");
            wordTwo.setSpan(new ForegroundColorSpan(Color.RED), 0, wordTwo.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ((TextView) layout.findViewById(R.id.riti_textView)).append(wordTwo);
        }

        if (Open311DataType.NUMBER.equals(open311Attribute.getData_type())) {
            ((EditText) layout.findViewById(R.id.riti_editText)).setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if (Open311DataType.DATETIME.equals(open311Attribute.getData_type())) {
            ((EditText) layout.findViewById(R.id.riti_editText)).setInputType(InputType.TYPE_CLASS_DATETIME);
        }

        linear.addView(layout);
    }

    /**
     * Dynamically creates radio buttons
     * @param open311Attribute contains the open311 attributes
     */
    private void createSingleValueList(Open311Attribute open311Attribute) {
        ArrayList<Object> values = (ArrayList<Object>) open311Attribute.getValues();
        if (values != null && values.size() > 0) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_single_value_list_item, null, false);

            LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);

            Spannable word = new SpannableString(open311Attribute.getDescription());
            ((TextView) layout.findViewById(R.id.risvli_textView)).setText(word);

            if (open311Attribute.getRequired()) {
                Spannable wordTwo = new SpannableString(" *Required");
                wordTwo.setSpan(new ForegroundColorSpan(Color.RED), 0, wordTwo.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ((TextView) layout.findViewById(R.id.risvli_textView)).append(wordTwo);
            }

            RadioGroup rg = (RadioGroup) layout.findViewById(R.id.risvli_radioGroup);
            rg.setOrientation(RadioGroup.VERTICAL);


            for (int i = 0; i < values.size(); i++) {
                LinkedHashMap<String, String> value = (LinkedHashMap<String, String>) values.get(i);
                RadioButton rb = new RadioButton(getActivity());
                rg.addView(rb); //the RadioButtons are added to the radioGroup instead of the layout
                for (LinkedHashMap.Entry<String, String> entry : value.entrySet()) {
                    Object obj = value.get(entry.getKey());
                    LinkedHashMap<String, String> val = (LinkedHashMap<String, String>) obj;
                    for (LinkedHashMap.Entry<String, String> entry2 : val.entrySet()) {
                        rb.setText(entry2.getValue());
                    }
                }
            }

            linear.addView(layout);
        }
    }

    /**
     * Dynamically creates checkboxes
     * @param open311Attribute contains the open311 attributes
     */
    private void createMultiValueList(Open311Attribute open311Attribute) {
        ArrayList<Object> values = (ArrayList<Object>) open311Attribute.getValues();
        if (values != null && values.size() > 0) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_multi_value_list_item, null, false);

            LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);

            Spannable word = new SpannableString(open311Attribute.getDescription());
            ((TextView) layout.findViewById(R.id.rimvli_textView)).setText(word);

            if (open311Attribute.getRequired()) {
                Spannable wordTwo = new SpannableString(" *Required");
                wordTwo.setSpan(new ForegroundColorSpan(Color.RED), 0, wordTwo.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ((TextView) layout.findViewById(R.id.rimvli_textView)).append(wordTwo);
            }

            LinearLayout cg = (LinearLayout) layout.findViewById(R.id.rimvli_checkBoxGroup);

            for (int i = 0; i < values.size(); i++) {
                LinkedHashMap<String, String> value = (LinkedHashMap<String, String>) values.get(i);
                CheckBox cb = new CheckBox(getActivity());
                cg.addView(cb);
                for (LinkedHashMap.Entry<String, String> entry : value.entrySet()) {
                    Object obj = value.get(entry.getKey());
                    LinkedHashMap<String, String> val = (LinkedHashMap<String, String>) obj;
                    for (LinkedHashMap.Entry<String, String> entry2 : val.entrySet()) {
                        cb.setText(entry2.getValue());
                    }
                }
            }

            linear.addView(layout);
        }
    }

    private void clearInfoField() {
        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);
        linear.removeAllViewsInLayout();
    }

    private void addInfoText(String text) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_info_item, null, false);

        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);
        ((TextView) layout.findViewById(R.id.riii_textView)).setText(text);
        linear.addView(layout);
    }

    private IssueLocationHelper getIssueLocationHelper() {
        return ((InfrastructureIssueActivity) getActivity()).getIssueLocationHelper();
    }

    private String getCurrentAddress() {
        return ((InfrastructureIssueActivity) getActivity()).getCurrentAddress();
    }

    private List<Service> getServiceList(){
        return ((InfrastructureIssueActivity) getActivity()).getServiceList();
    }

    private void showProgress(Boolean visible){
        ((InfrastructureIssueActivity) getActivity()).showProgress(visible);
    }

    private void createContactInfoFragment(){
        ((InfrastructureIssueActivity) getActivity()).createContactInfoFragment();
    }
}
