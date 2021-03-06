package com.xpto.legion;

import org.json.JSONObject;

import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;

import com.xpto.legion.data.Caller;
import com.xpto.legion.models.User;
import com.xpto.legion.utils.LActivity;
import com.xpto.legion.utils.LCallback;
import com.xpto.legion.utils.LDialog;
import com.xpto.legion.utils.LFragment;
import com.xpto.legion.utils.Util;

public class FrgNewUser extends LFragment {
	private EditText txtLogin;
	private EditText txtPass1;
	private EditText txtPass2;
	private Button btnRegister;

	private View viwHelp;

	@Override
	public View createView(LayoutInflater inflater) {
		View view = inflater.inflate(R.layout.frg_new_user, null);

		Util.loadFonts(view);

		txtLogin = (EditText) view.findViewById(R.id.txtLogin);
		txtPass1 = (EditText) view.findViewById(R.id.txtPass1);
		txtPass2 = (EditText) view.findViewById(R.id.txtPass2);
		btnRegister = (Button) view.findViewById(R.id.btnRegister);
		btnRegister.setOnClickListener(onClickRegister);

		Help.fillHelpNewUser(viwHelp = view.findViewById(R.id.layHelp));

		return view;
	}

	@Override
	public Animation getInAnimation() {
		Animation cameIn = AnimationUtils.loadAnimation(getActivity(), R.anim.transition_dialog_in);
		cameIn.setAnimationListener(new AnimationListener() {
			@Override
			public void onAnimationStart(Animation animation) {
			}

			@Override
			public void onAnimationRepeat(Animation animation) {
			}

			@Override
			public void onAnimationEnd(Animation animation) {
				txtLogin.requestFocus();
				Util.showKeyboard(txtLogin);
			}
		});
		return cameIn;
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

	private View.OnClickListener onClickRegister = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			String login = txtLogin.getText().toString();
			boolean hasSpecial = false;

			String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-";
			for (int i = 0; i < login.length(); i++)
				if (chars.indexOf(login.charAt(i)) == -1) {
					hasSpecial = true;
					break;
				}

			if (hasSpecial || login.length() < 4 || login.length() > 32) {
				LDialog.openDialog((LActivity) getActivity(), R.string.f_newuser_fill_login_title, R.string.f_newuser_fill_login_subtitle, R.string.f_ok, false);
				return;
			}

			String pass = txtPass1.getText().toString();
			if (pass.length() < 4 || pass.length() > 32) {
				LDialog.openDialog((LActivity) getActivity(), R.string.f_newuser_fill_pass_title, R.string.f_newuser_fill_pass_subtitle, R.string.f_ok, false);
				return;
			}

			String pass2 = txtPass2.getText().toString();
			if (!pass.equals(pass2)) {
				LDialog.openDialog((LActivity) getActivity(), R.string.f_newuser_fill_pass2_title, R.string.f_newuser_fill_pass2_subtitle, R.string.f_ok, false);
				return;
			}

			newUserRetry.finished(null);
			Caller.newUser(getActivity(), newUserSuccess, newUserRetry, newUserFail, login, pass);
		}
	};

	private LCallback newUserSuccess = new LCallback() {
		@Override
		public void finished(Object _value) {
			if (getActivity() != null)
				((LActivity) getActivity()).endLoading();

			txtLogin.setEnabled(true);
			txtPass1.setEnabled(true);
			txtPass2.setEnabled(true);
			btnRegister.setEnabled(true);

			try {
				if (_value == null || !(_value instanceof JSONObject))
					return;

				JSONObject json = (JSONObject) _value;
				if (json.getLong("Code") == 1) {

					// Log in
					User logged = new User();
					logged.setId(json.getLong("Content"));
					logged.setLogin(txtLogin.getText().toString());
					getGlobal().setLogged(logged);

					canBack();
				} else if (json.getLong("Code") == 5)
					LDialog.openDialog((LActivity) getActivity(), R.string.f_newuser_fill_login_title, R.string.f_newuser_fill_login_exists, R.string.f_ok,
							false);
				else
					throw new Exception();
			} catch (Exception e) {
			}
		}
	};

	private LCallback newUserRetry = new LCallback() {
		@Override
		public void finished(Object _value) {
			if (getActivity() != null)
				((LActivity) getActivity()).startLoading(R.string.f_saving);

			txtLogin.setEnabled(false);
			txtPass1.setEnabled(false);
			txtPass2.setEnabled(false);
			btnRegister.setEnabled(false);
		}
	};

	private LCallback newUserFail = new LCallback() {
		@Override
		public void finished(Object _value) {
			LDialog.openDialog((LActivity) getActivity(), R.string.f_no_connection, R.string.f_newuser_fail, R.string.f_ok, false);
		}
	};
}
