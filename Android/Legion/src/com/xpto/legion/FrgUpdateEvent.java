package com.xpto.legion;

import java.util.Calendar;

import org.json.JSONObject;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TimePicker;

import com.xpto.legion.data.Caller;
import com.xpto.legion.models.Place;
import com.xpto.legion.utils.LActivity;
import com.xpto.legion.utils.LCallback;
import com.xpto.legion.utils.LDialog;
import com.xpto.legion.utils.LFragment;
import com.xpto.legion.utils.Util;

public class FrgUpdateEvent extends LFragment {
	private Place place;

	private EditText txtName;
	private EditText txtDescription;
	private EditText txtDate;
	private Button btnRegister;

	private Calendar when;
	private DatePickerDialog datePickerDialog;
	private TimePickerDialog timePickerDialog;

	private View viwHelp;

	@Override
	public View createView(LayoutInflater inflater) {
		View view = inflater.inflate(R.layout.frg_update_event, null);

		Util.loadFonts(view);

		txtName = (EditText) view.findViewById(R.id.txtName);
		txtDescription = (EditText) view.findViewById(R.id.txtDescription);
		txtDate = (EditText) view.findViewById(R.id.txtDate);
		txtDate.setOnFocusChangeListener(onFocusDate);
		btnRegister = (Button) view.findViewById(R.id.btnRegister);
		btnRegister.setOnClickListener(onClickRegister);

		fill();

		Help.fillHelpUpdateEvent(viwHelp = view.findViewById(R.id.layHelp));

		return view;
	}

	@Override
	public Animation getInAnimation() {
		return AnimationUtils.loadAnimation(getActivity(), R.anim.transition_dialog_in);
	}

	@Override
	public Animation getOutAnimation() {
		return AnimationUtils.loadAnimation(getActivity(), R.anim.transition_dialog_out);
	}

	@Override
	public boolean canBack() {
		((ActMain) getActivity()).setFragment(null, ActMain.LEVEL_TOP);
		return false;
	}

	@Override
	public void showHelp() {
		Animation cameIn = AnimationUtils.loadAnimation(getActivity(), R.anim.transition_dialog_in);
		viwHelp.setVisibility(View.VISIBLE);
		viwHelp.startAnimation(cameIn);
	}

	public void setPlace(Place _place) {
		place = _place;
		if (when == null)
			when = Calendar.getInstance();

		fill();
	}

	private void fill() {
		if (place != null && txtName != null) {
			txtName.setText(place.getName());
			txtDescription.setText(place.getDescription());
			when = Calendar.getInstance();
			when.setTime(place.getWhen());
			setDateText();
		}
	}

	private View.OnFocusChangeListener onFocusDate = new View.OnFocusChangeListener() {
		@Override
		public void onFocusChange(View v, boolean hasFocus) {
			if (hasFocus) {
				if (datePickerDialog == null) {
					datePickerDialog = new DatePickerDialog(getActivity(), dpListener, when.get(Calendar.YEAR), when.get(Calendar.MONTH) + 1,
							when.get(Calendar.DAY_OF_MONTH));
					datePickerDialog.show();
				}
			}
		}
	};

	private DatePickerDialog.OnDateSetListener dpListener = new DatePickerDialog.OnDateSetListener() {
		@Override
		public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
			when.set(Calendar.YEAR, year);
			when.set(Calendar.MONTH, monthOfYear);
			when.set(Calendar.DAY_OF_MONTH, dayOfMonth);

			setDateText();

			if (timePickerDialog == null) {
				timePickerDialog = new TimePickerDialog(getActivity(), tpListener, when.get(Calendar.HOUR_OF_DAY), when.get(Calendar.MINUTE), true);
				timePickerDialog.show();
			}
		}
	};

	private TimePickerDialog.OnTimeSetListener tpListener = new TimePickerDialog.OnTimeSetListener() {
		@Override
		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			when.set(Calendar.HOUR_OF_DAY, hourOfDay);
			when.set(Calendar.MINUTE, minute);

			setDateText();

			datePickerDialog = null;
			timePickerDialog = null;

			txtDescription.requestFocus();
		}
	};

	private void setDateText() {
		String y = when.get(Calendar.YEAR) + "";
		String M = (when.get(Calendar.MONTH) < 9 ? "0" : "") + (when.get(Calendar.MONTH) + 1);
		String d = (when.get(Calendar.DAY_OF_MONTH) < 10 ? "0" : "") + when.get(Calendar.DAY_OF_MONTH);
		String h = (when.get(Calendar.HOUR_OF_DAY) < 10 ? "0" : "") + when.get(Calendar.HOUR_OF_DAY);
		String m = (when.get(Calendar.MINUTE) < 10 ? "0" : "") + when.get(Calendar.MINUTE);

		txtDate.setText(d + "/" + M + "/" + y + " " + h + ":" + m);
	}

	private View.OnClickListener onClickRegister = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			String name = txtName.getText().toString();
			if (name.length() < 2 || name.length() > 32) {
				LDialog.openDialog((LActivity) getActivity(), R.string.f_updateevent_fill_name_title, R.string.f_updateevent_fill_name_subtitle, R.string.f_ok, false);
				return;
			}

			String description = txtDescription.getText().toString();
			if (description.length() < 4) {
				LDialog.openDialog((LActivity) getActivity(), R.string.f_updateevent_fill_description_title, R.string.f_updateevent_fill_description_subtitle,
						R.string.f_ok, false);
				return;
			}

			place.setName(name);
			place.setDescription(description);
			place.setWhen(when.getTime());

			newPlaceRetry.finished(null);
			Caller.updatePlace(getActivity(), newPlaceSuccess, newPlaceRetry, newPlaceFail, getGlobal().getLogged().getId(), place.getId(),
					place.getLatitude(), place.getLongitude(), name, description, when);

			// long user, long place, double latitude,
			// double longitude, String name, String description, Calendar when
		}
	};

	private LCallback newPlaceSuccess = new LCallback() {
		@Override
		public void finished(Object _value) {
			if (getActivity() != null)
				((LActivity) getActivity()).endLoading();

			txtName.setEnabled(true);
			txtDescription.setEnabled(true);
			txtDate.setEnabled(true);
			btnRegister.setEnabled(true);

			try {
				if (_value == null || !(_value instanceof JSONObject))
					return;

				JSONObject json = (JSONObject) _value;
				if (json.getLong("Code") == 1) {
					canBack();
				} else if (json.getLong("Code") == 6) {
					if (getActivity() != null)
						LDialog.openDialog((LActivity) getActivity(), R.string.f_updateevent_rating_title, R.string.f_updateevent_rating_subtitle, R.string.f_ok,
								false);
				} else
					throw new Exception();
			} catch (Exception e) {
				newPlaceFail.finished(null);
			}
		}
	};

	private LCallback newPlaceRetry = new LCallback() {
		@Override
		public void finished(Object _value) {
			if (getActivity() != null)
				((LActivity) getActivity()).startLoading(R.string.f_saving);

			txtName.setEnabled(false);
			txtDescription.setEnabled(false);
			txtDate.setEnabled(false);
			btnRegister.setEnabled(false);
		}
	};

	private LCallback newPlaceFail = new LCallback() {
		@Override
		public void finished(Object _value) {
			if (getActivity() != null)
				LDialog.openDialog((LActivity) getActivity(), R.string.f_no_connection, R.string.f_newuser_fail, R.string.f_ok, false);
		}
	};
}
