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
import org.onebusaway.android.io.elements.ObaStop;
import org.onebusaway.android.report.connection.ServiceDescriptionTask;
import org.onebusaway.android.report.connection.ServiceRequestTask;
import org.onebusaway.android.report.constants.ReportConstants;
import org.onebusaway.android.report.ui.dialog.ReportSuccessDialog;
import org.onebusaway.android.report.ui.model.AttributeValue;
import org.onebusaway.android.report.ui.util.IssueLocationHelper;
import org.onebusaway.android.util.PreferenceHelp;

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
import android.os.Parcelable;
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

/**
 * Created by Cagri Cetin
 */
public class Open311ProblemFragment extends BaseReportFragment implements
        ServiceDescriptionTask.Callback, ServiceRequestTask.Callback {

    /**
     * UI elements
     */
    private ImageView mIssueImage;

    private Open311 mOpen311;

    private Service mService;

    // Captured image url
    private Uri mCapturedImageURI;

    // Open311 service description result for selected service code
    private ServiceDescription mServiceDescription;

    private LinearLayout mInfoLayout;

    private Map<Integer, AttributeValue> mAttributeValueHashMap = new HashMap<>();

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

        setupViews();

        setupIconColors();

        setUpContactInfoViews();

        callServiceDescription();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        List<AttributeValue> attributeValues = createAttributeValues(mServiceDescription);
        outState.putParcelableArrayList("test", (ArrayList<? extends Parcelable>) attributeValues);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            List<AttributeValue> values = savedInstanceState.getParcelableArrayList("test");
            for (AttributeValue v : values) {
                mAttributeValueHashMap.put(v.getCode(), v);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        ObaAnalytics.reportFragmentStart(this);
    }

    /**
     * Initialize UI components
     */
    private void setupViews() {
        mIssueImage = (ImageView) findViewById(R.id.ri_imageView);
        mIssueImage.setSaveEnabled(true);

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
            ObaAnalytics.reportEventWithCategory(ObaAnalytics.ObaEventCategory.SUBMIT.toString(),
                    getString(R.string.analytics_action_problem), getString(R.string.analytics_label_report_infrastructure));
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
        description = description + getBusStopInfo();

        final TelephonyManager tm = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);

        Open311User open311User = getOpen311UserFromUI();

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
                mOpen311.getOpen311Option().getOpen311Type(), mServiceDescription);

        List<Open311AttributePair> attributes = createOpen311Attributes(mServiceDescription);
        serviceRequest.setAttributes(attributes);

        if (Open311Validator.isValid(errorCode)) {
            //Start progress
            showProgress(Boolean.TRUE);

            ServiceRequestTask srt = new ServiceRequestTask(mOpen311, serviceRequest, this);
            srt.execute();
        } else {
            createToastMessage(Open311Validator.getErrorMessageForServiceRequestByErrorCode(errorCode));
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

    private List<AttributeValue> createAttributeValues(ServiceDescription serviceDescription) {
        List<AttributeValue> values = new ArrayList<>();
        for (Open311Attribute open311Attribute : serviceDescription.getAttributes()) {
            if (Boolean.valueOf(open311Attribute.getVariable())) {
                if (Open311DataType.STRING.equals(open311Attribute.getDatatype())
                        || Open311DataType.NUMBER.equals(open311Attribute.getDatatype())
                        || Open311DataType.DATETIME.equals(open311Attribute.getDatatype())) {
                    EditText et = (EditText) findViewById(R.id.riti_editText);
                    if (et != null) {
                        AttributeValue value = new AttributeValue(open311Attribute.getCode());
                        value.addValue(et.getText().toString());
                        values.add(value);
                    }
                } else if (Open311DataType.SINGLEVALUELIST.equals(open311Attribute.getDatatype())) {
                    RadioGroup rg = (RadioGroup) findViewById(R.id.risvli_radioGroup);
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
                    LinearLayout ll = (LinearLayout) findViewById(R.id.rimvli_checkBoxGroup);
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

        ((ImageView) layout.findViewById(R.id.ri_ic_question_answer)).setColorFilter(
                getResources().getColor(R.color.material_gray));

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

        // Restore view state from attribute result hash map
        AttributeValue av = mAttributeValueHashMap.get(open311Attribute.getCode());
        if (av != null){
            editText.setText(av.getSingleValue());
        }

        mInfoLayout.addView(layout);
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
            layout.setSaveEnabled(true);
            ((ImageView) layout.findViewById(R.id.ri_ic_radio)).setColorFilter(
                    getResources().getColor(R.color.material_gray));

            Spannable word = new SpannableString(open311Attribute.getDescription());
            ((TextView) layout.findViewById(R.id.risvli_textView)).setText(word);

            if (open311Attribute.getRequired()) {
                Spannable wordTwo = new SpannableString(" *Required");
                wordTwo.setSpan(new ForegroundColorSpan(Color.RED), 0, wordTwo.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
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
                for (LinkedHashMap.Entry<String, String> entry : value.entrySet()) {
                    rb.setText(entry.getValue());
                    if (entryValue != null && entryValue.equalsIgnoreCase(entry.getValue())){
                        rb.setChecked(true);
                    }
                }
            }

            mInfoLayout.addView(layout);
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
                for (LinkedHashMap.Entry<String, String> entry : value.entrySet()) {
                    cb.setText(entry.getValue());

                    if (av != null && av.getValues().contains(entry.getValue())) {
                        cb.setChecked(true);
                    }
                }
            }

            mInfoLayout.addView(layout);
        }
    }

    private void clearInfoField() {
        mInfoLayout.removeAllViewsInLayout();
    }

    private void addDescriptionText(String text) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        RelativeLayout layout = (RelativeLayout) inflater.inflate(R.layout.report_issue_description_item, null, false);

        ((TextView) layout.findViewById(R.id.riii_textView)).setText(text);
        mInfoLayout.addView(layout);

        ((ImageView) layout.findViewById(R.id.ic_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    private void setUpContactInfoViews() {
        Open311User open311User = getOpen311UserFromSharedPref();
        if (open311User.getName() != null)
            ((EditText) findViewById(R.id.rici_name_editText)).setText(open311User.getName());
        if (open311User.getLastName() != null)
            ((EditText) findViewById(R.id.rici_lastname_editText)).setText(open311User.getLastName());
        if (open311User.getEmail() != null)
            ((EditText) findViewById(R.id.rici_email_editText)).setText(open311User.getEmail());
        if (open311User.getPhone() != null)
            ((EditText) findViewById(R.id.rici_phone_editText)).setText(open311User.getPhone());
    }

    /**
     * Get open311 user from shared preferences
     *
     * @return Open311User
     */
    private Open311User getOpen311UserFromSharedPref() {
        return new Open311User(PreferenceHelp.getString(ReportConstants.PREF_NAME),
                PreferenceHelp.getString(ReportConstants.PREF_LASTNAME),
                PreferenceHelp.getString(ReportConstants.PREF_EMAIL),
                PreferenceHelp.getString(ReportConstants.PREF_PHONE));
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

    private void saveOpen311User() {
        String name = ((EditText) findViewById(R.id.rici_name_editText)).getText().toString();
        String lastName = ((EditText) findViewById(R.id.rici_lastname_editText)).getText().toString();
        String email = ((EditText) findViewById(R.id.rici_email_editText)).getText().toString();
        String phone = ((EditText) findViewById(R.id.rici_phone_editText)).getText().toString();

        PreferenceHelp.saveString(ReportConstants.PREF_NAME, name);
        PreferenceHelp.saveString(ReportConstants.PREF_LASTNAME, lastName);
        PreferenceHelp.saveString(ReportConstants.PREF_EMAIL, email);
        PreferenceHelp.saveString(ReportConstants.PREF_PHONE, phone);
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

    public void setOpen311(Open311 open311) {
        mOpen311 = open311;
    }

    public void setService(Service service) {
        mService = service;
    }
}
