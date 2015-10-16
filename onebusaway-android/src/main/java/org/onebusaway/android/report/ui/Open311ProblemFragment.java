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
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.report.connection.ServiceDescriptionTask;
import org.onebusaway.android.report.connection.ServiceRequestTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.dialog.ReportSuccessDialog;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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

/**
 * Created by Cagri Cetin
 */
public class Open311ProblemFragment extends BaseReportFragment implements
        ServiceDescriptionTask.Callback, ServiceRequestTask.Callback {

    /**
     * UI elements
     */
    private ImageView issueImage;

    private Open311 mOpen311;

    private Service mService;

    //Captured image url
    private Uri mCapturedImageURI;

    //Open311 service description result for selected service code
    private ServiceDescription serviceDescription;

    public static final String TAG = "Open311ProblemFragment";

    public static void show(AppCompatActivity activity, Integer containerViewId,
                            Open311 open311, Service service) {
        FragmentManager fm = activity.getSupportFragmentManager();

        Open311ProblemFragment fragment = new Open311ProblemFragment();
        fragment.setOpen311(open311);
        fragment.setService(service);

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

        callServiceDescription();
    }

    /**
     * Initialize UI components
     */
    private void setupViews() {
        issueImage = (ImageView) findViewById(R.id.ri_imageView);

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
        showProgress(Boolean.FALSE);
        if (serviceDescription.isSuccess()) {
            createServiceDescriptionUI(serviceDescription);
        } else {
            createToastMessage(getString(R.string.ri_service_description_problem));
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
            createOrRemoveContactInfoFragment();
        } else if (id == R.id.report_problem_send) {
            submitReport();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ReportConstants.GALLERY_INTENT && resultCode == Activity.RESULT_OK &&
                null != data) {
            mCapturedImageURI = data.getData();
            String[] filePathColumn = {MediaStore.Images.Media.DATA};
            Cursor cursor = getActivity().getContentResolver().query(mCapturedImageURI,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);
            cursor.close();
            issueImage.setImageBitmap(BitmapFactory.decodeFile(picturePath));
        }
    }

    /**
     * Prepare submit forms and submit report
     */
    private void submitReport() {
        //Prepare issue description
        String description = ((EditText) getActivity().findViewById(R.id.ri_editTextDesc)).getText().toString();
        description = description + getBusStopInfo();

        final TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        Open311User open311User = getOpen311User();

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
            builder.setMedia_url(mCapturedImageURI.toString());
            try {
                builder.setMedia(createImageFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ServiceRequest serviceRequest = builder.createServiceRequest();
        int errorCode = Open311Validator.validateServiceRequest(serviceRequest,
                mOpen311.getOpen311Option().getOpen311Type(), serviceDescription);

        List<Open311AttributePair> attributes = createOpen311Attributes(serviceDescription);
        serviceRequest.setAttributes(attributes);

        if (Open311Validator.isValid(errorCode)) {
            //Start progress
            showProgress(Boolean.TRUE);

            ServiceRequestTask srt = new ServiceRequestTask(mOpen311, serviceRequest, this);
            srt.execute();
        } else {
            createToastMessage(Open311Validator.getErrorMessageForServiceRequestByErrorCode(errorCode));

            if (errorCode == Open311Validator.PROBLEM_CODE_USER_NAME ||
                    errorCode == Open311Validator.PROBLEM_CODE_USER_LASTNAME ||
                    errorCode == Open311Validator.PROBLEM_CODE_USER_EMAIL)
                createOrRemoveContactInfoFragment();
        }
    }

    /**
     * Creates current bus stop information for current selected bus stop
     *
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
                    EditText et = (EditText) findViewById(R.id.riti_editText);
                    if (et != null) {
                        attributes.add(new Open311AttributePair(open311Attribute.getCode(),
                                et.getText().toString(), open311Attribute.getDatatype()));
                    }
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getDatatype())) {
                    RadioGroup rg = (RadioGroup) findViewById(R.id.risvli_radioGroup);
                    if (rg != null) {
                        int count = rg.getChildCount();
                        for (int i = 0; i < count; i++) {
                            RadioButton rb = (RadioButton) rg.getChildAt(i);
                            if (rb.isChecked()) {
                                attributes.add(new Open311AttributePair(open311Attribute.getCode(),
                                        rb.getText().toString(), open311Attribute.getDatatype()));
                                break;
                            }
                        }
                    }
                } else if (Open311DataType.MULTIVALUELIST.equals(open311Attribute.getDatatype())) {
                    LinearLayout ll = (LinearLayout) findViewById(R.id.rimvli_checkBoxGroup);
                    if (ll != null) {
                        int count = ll.getChildCount();
                        for (int i = 0; i < count; i++) {
                            CheckBox cb = (CheckBox) ll.getChildAt(i);
                            if (cb.isChecked()) {
                                attributes.add(new Open311AttributePair(open311Attribute.getCode(),
                                        cb.getText().toString(), open311Attribute.getDatatype()));
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
     *
     * @return image in bytes
     */
    private File createImageFile() throws IOException {
        //Convert bitmap to file
        Bitmap bitmap = ((BitmapDrawable) issueImage.getDrawable()).getBitmap();
        String path = Environment.getExternalStorageDirectory().toString() + "/" + "Download";
        File file = new File(path, "image.jpg");
        OutputStream fOut;
        fOut = new FileOutputStream(file);

        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fOut);
        fOut.flush();
        fOut.close();

        return file;
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
     *
     * @param serviceDescription contains Open311 questions
     */
    public void createServiceDescriptionUI(ServiceDescription serviceDescription) {
        clearInfoField();
        this.serviceDescription = serviceDescription;
        if (!"".equals(mService.getDescription()) && mService.getDescription() != null) {
            addDescriptionText(mService.getDescription());
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


        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);
        Spannable desc = new SpannableString(open311Attribute.getDescription());
        EditText editText = ((EditText) layout.findViewById(R.id.riti_editText));
        if (open311Attribute.getRequired()) {
            Spannable req = new SpannableString("(Required)");
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

        linear.addView(layout);
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
                    rb.setText(entry.getValue());
                }
            }

            linear.addView(layout);
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
                    cb.setText(entry.getValue());
                }
            }

            linear.addView(layout);
        }
    }

    private void clearInfoField() {
        LinearLayout linear = (LinearLayout) findViewById(R.id.ri_info_layout);
        linear.removeAllViewsInLayout();
    }

    private void addDescriptionText(String text) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_description_item, null, false);

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

    private void showProgress(Boolean visible) {
        ((InfrastructureIssueActivity) getActivity()).showProgress(visible);
    }

    private void createOrRemoveContactInfoFragment() {
        ((InfrastructureIssueActivity) getActivity()).createOrRemoveContactInfoFragment();
    }

    public void setOpen311(Open311 open311) {
        mOpen311 = open311;
    }

    public void setService(Service service) {
        mService = service;
    }
}
