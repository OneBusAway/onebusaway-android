package org.onebusaway.android.report.ui.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

import edu.usf.cutr.open311client.models.Open311Attribute;

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
        parcel.writeStringArray((String[]) values.toArray());
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
        this.values.add(value);
    }

    public String getSingleValue() {
        if (values.size() == 0) {
            return "";
        } else {
            return values.get(0);
        }
    }
}
