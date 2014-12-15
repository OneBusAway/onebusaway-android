package org.onebusaway.android.report.ui.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.util.ArrayList;

public class AttributeValue implements Parcelable {

    private Integer code;

    private ArrayList<String> values = new ArrayList<>();

    public AttributeValue(Integer code) {
        this.code = code;
    }

    protected AttributeValue(Parcel in) {
        code = in.readInt();
    }

    public static final Creator<AttributeValue> CREATOR = new Creator<AttributeValue>() {
        @Override
        public AttributeValue createFromParcel(Parcel in) {
            return new AttributeValue(in);
        }

        @Override
        public AttributeValue[] newArray(int size) {
            return new AttributeValue[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(code);
        try {
            parcel.writeStringArray((String[]) values.toArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public ArrayList<String> getValues() {
        return values;
    }

    public void addValue(String value) {
        if (!TextUtils.isEmpty(value)) {
            this.values.add(value);
        }
    }

    public String getSingleValue() {
        if (values.size() == 0) {
            return "";
        } else {
            return values.get(0);
        }
    }
}
