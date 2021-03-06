package com.xpto.legion.models;

import java.util.Date;

import org.json.JSONObject;

import com.xpto.legion.utils.Util;

public class Notification extends Default {
	private long customId;

	public long getCustomId() {
		return customId;
	}

	public void setCustomId(long _customId) {
		if (_customId >= 0)
			customId = _customId;
	}

	private byte customTypeId;

	public byte getCustomTypeId() {
		return customTypeId;
	}

	public void setCustomTypeId(byte _customTypeId) {
		if (_customTypeId >= 0)
			customTypeId = _customTypeId;
	}

	private long what;

	public long getWhat() {
		return what;
	}

	public void setWhat(long _what) {
		if (_what >= 0)
			what = _what;
	}

	private Date when;

	public Date getWhen() {
		return when;
	}

	public void setWhen(Date _when) {
		if (_when != null)
			when = _when;
	}

	private boolean seen;

	public boolean getSeen() {
		return seen;
	}

	public void setSeen(boolean _seen) {
		seen = _seen;
	}

	private long quantity;

	public long getQuantity() {
		return quantity;
	}

	public void setQuantity(long _quantity) {
		if (_quantity >= 0)
			quantity = _quantity;
	}

	@Override
	public boolean loadFromJSon(JSONObject _json) {
		try {
			if (_json == null)
				return false;

			if (hasValue(_json, "CustomId"))
				setCustomId(_json.getLong("CustomId"));

			if (hasValue(_json, "CustomTypeId"))
				setCustomTypeId((byte) _json.getInt("CustomTypeId"));

			if (hasValue(_json, "What"))
				setWhat(_json.getLong("What"));

			if (hasValue(_json, "Date"))
				setWhen(Util.parseJSONDate(_json.getString("Date")));

			if (hasValue(_json, "Seen"))
				setSeen(_json.getBoolean("Seen"));

			if (hasValue(_json, "Quantity"))
				setQuantity(_json.getLong("Quantity"));

			setId(getCustomId() * 100 + getCustomTypeId() * 10 + getWhat());

			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
