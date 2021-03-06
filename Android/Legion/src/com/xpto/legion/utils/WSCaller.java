package com.xpto.legion.utils;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.xpto.legion.R;
import com.xpto.legion.data.Cache;
import com.xpto.legion.data.DB;

public class WSCaller {
	// Cache types
	public static final long WS_CACHE_NO = Long.MAX_VALUE;
	public static final long WS_CACHE_FAST = 0;
	public static final long WS_CACHE_1MIN = 1000 * 60;
	public static final long WS_CACHE_1DAY = WS_CACHE_1MIN * 60 * 24;
	public static final long WS_CACHE_1WEEK = WS_CACHE_1DAY * 7;
	public static final long WS_CACHE_EVER = WS_CACHE_1DAY * 365;

	private static final String offlineException = "Offline";

	// Offline dialog control
	private static long lastNo = 0;
	private static boolean showingOfflineDialog = false;

	private static boolean isShowingOfflineDialog() {
		if (showingOfflineDialog)
			return true;
		else if (System.currentTimeMillis() - lastNo < WS_CACHE_1MIN)
			return true;
		else
			return false;
	}

	private static void isShowingOfflineDialog(boolean value) {
		showingOfflineDialog = value;
	}

	// Try again (ws calls) pool control
	private static ArrayList<LCallback> tryAgainPool;
	private static ArrayList<LCallback> tryAgainFail;

	// Add callback to be used when try again is called
	private static void addToTryAgainPool(LCallback callback, LCallback failCallback) {
		if (tryAgainPool == null) {
			tryAgainPool = new ArrayList<LCallback>();
			tryAgainFail = new ArrayList<LCallback>();
		}
		tryAgainPool.add(callback);
		tryAgainFail.add(failCallback);
	}

	private static LCallback getFromTryAgainPool() {
		if (tryAgainPool == null || tryAgainPool.size() == 0)
			return null;
		tryAgainFail.remove(0);
		return tryAgainPool.remove(0);
	}

	private static void clearTryAgainPool() {
		if (tryAgainPool != null && tryAgainPool.size() > 0) {
			while (tryAgainPool.size() > 0) {
				if (tryAgainFail.get(0) != null)
					tryAgainFail.get(0).finished(null);
				tryAgainFail.remove(0);
				tryAgainPool.remove(0);
			}
		}
	}

	public static void asyncCall(final Activity activity, final LCallback callback, final LCallback retryCallback, final LCallback failCallback,
			final String server, final String fn, final Object parameters, final long cache_type, final int retryTimes, final boolean showRetryDialog) {
		if (parameters != null && !(parameters instanceof JSONObject) && !(parameters instanceof JSONArray)) {
			callback.finished(null);
			return;
		}

		if (isShowingOfflineDialog() && ((LActivity) activity).getDialogs().size() == 0)
			isShowingOfflineDialog(false);

		new Thread() {
			@Override
			public void run() {
				try {
					// Create url
					String urlServer = server + (fn != null && fn.length() > 0 ? fn : "");
					String urlCache = urlServer;

					// Can use cache?
					if (cache_type != WS_CACHE_NO) {
						// Create cache url
						if (parameters != null && parameters instanceof JSONObject) {
							JSONObject json = (JSONObject) parameters;
							if (json.names() != null)
								for (int i = 0; i < json.names().length(); i++) {
									String name = json.names().getString(i);
									urlCache += "&" + name + "=" + json.get(name).toString();
								}
						}

						// Get cache
						final Cache cache = DB.get(urlCache);
						if (cache != null) {
							if (callback != null && activity != null) {
								// Return last cache
								if (cache.getValue().length() > 0 && cache.getValue().charAt(0) == '[') {
									final JSONArray jarray = new JSONArray(cache.getValue());
									activity.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											callback.finished(jarray);
										}
									});
								} else {
									final JSONObject json = new JSONObject(cache.getValue());
									activity.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											callback.finished(json);
										}
									});
								}
							}

							if (cache.getWhen() >= new Date().getTime() - cache_type)
								// End if cache is valid, but continue if not
								return;
						}
					}

					// If there is no connectivity, throws exception
					if (!isOnline(activity))
						throw new Exception(offlineException);

					// Prepare connection
					HttpParams httpParams = new BasicHttpParams();
					HttpConnectionParams.setConnectionTimeout(httpParams, 60000);
					HttpConnectionParams.setSoTimeout(httpParams, 60000);
					HttpClient httpclient = new DefaultHttpClient(httpParams);

					// Add parameters
					HttpPost httppost = new HttpPost(urlServer);
					if (parameters != null) {
						StringEntity se = new StringEntity(parameters.toString(), HTTP.UTF_8);
						httppost.setEntity(se);
					}
					httppost.setHeader("Accept", "application/json");
					httppost.setHeader("Content-type", "application/json");

					HttpResponse response = httpclient.execute(httppost);
					String body = EntityUtils.toString(response.getEntity());
					String content;
					if (body.indexOf("Result\":") > 0 && body.indexOf(":") < body.length() - 1)
						content = body.substring(body.indexOf(":") + 1, body.length() - 1);
					else
						content = body;

					// Return response content to callback
					if (callback != null && activity != null) {
						if (content.length() > 0 && content.charAt(0) == '[') {
							// Convert json array
							final JSONArray jarray = new JSONArray(content);
							activity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									callback.finished(jarray);
								}
							});

							// Hold cache when possible
							if (cache_type != WS_CACHE_NO && jarray.toString().length() > 0)
								DB.set(urlCache, jarray.toString());
						} else {
							// Convert json data
							final JSONObject json = new JSONObject(content);
							activity.runOnUiThread(new Runnable() {
								@Override
								public void run() {
									callback.finished(json);
								}
							});

							// Hold cache when possible
							if (cache_type != WS_CACHE_NO && json.toString().length() > 0)
								DB.set(urlCache, json.toString());
						}
					}
				} catch (final Exception e1) {
					// If there is no connectivity don't retry
					if (!offlineException.equals(e1.getMessage()) && retryTimes > 0) {
						// Retry this call with retry times -1
						asyncCall(activity, callback, retryCallback, failCallback, server, fn, parameters, cache_type, retryTimes - 1, showRetryDialog);
						return;
					} else if (callback != null && activity != null)
						// Return null, indicating unknown error or no
						// connectivity error
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								callback.finished(null);
							}
						});

					try {
						// Make threads randomly work in different times to
						// avoid double dialogs
						sleep(new Random().nextInt(100) + 10);
					} catch (Exception e) {
					}

					// Test if connection dialog may be showed
					if (showRetryDialog && !isShowingOfflineDialog() && ((LActivity) activity).isActivityVisible()) {
						isShowingOfflineDialog(true); // Block dialog

						// Call ui thread to open dialog
						activity.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								try {
									LDialog.openDialog((LActivity) activity, R.string.f_no_connection, R.string.f_to_try_again, R.string.f_no, true,
											R.string.f_yes, false, new LDialog.DialogResult() {
												@Override
												public void result(int result, String info) {
													// Free dialog
													isShowingOfflineDialog(false);

													switch (result) {
													case LDialog.BUTTON1:
														lastNo = System.currentTimeMillis();
														activity.runOnUiThread(new Runnable() {
															@Override
															public void run() {
																if (failCallback != null)
																	failCallback.finished(null);
																clearTryAgainPool();
															}
														});
														break;

													case LDialog.BUTTON2:
														if (retryCallback != null) {
															// say to activity,
															// it that will try
															// again
															activity.runOnUiThread(new Runnable() {
																@Override
																public void run() {
																	retryCallback.finished(null);
																}
															});
														}

														// Redo this call
														asyncCall(activity, callback, retryCallback, failCallback, server, fn, parameters, cache_type,
																retryTimes, showRetryDialog);

														// Start pending/waiting
														// calls
														LCallback poolCallbak;
														while ((poolCallbak = getFromTryAgainPool()) != null)
															poolCallbak.finished(true);
														break;

													default:
														activity.runOnUiThread(new Runnable() {
															@Override
															public void run() {
																if (failCallback != null)
																	failCallback.finished(null);
																clearTryAgainPool();
															}
														});
														break;
													}
												}
											});
								} catch (Exception ex) {
									// Occurs with window leak (the user has
									// left the app)

									// Stop pending/waiting calls
									activity.runOnUiThread(new Runnable() {
										@Override
										public void run() {
											if (failCallback != null)
												failCallback.finished(null);
											clearTryAgainPool();
										}
									});
								}
							}
						});
					} else {
						// Add event to be called on try again or fail
						if (!showRetryDialog || System.currentTimeMillis() - lastNo < WS_CACHE_1MIN || !((LActivity) activity).isActivityVisible()) {
							if (failCallback != null)
								activity.runOnUiThread(new Runnable() {
									@Override
									public void run() {
										failCallback.finished(null);
									}
								});
						} else {
							addToTryAgainPool(new LCallback() {
								@Override
								public void finished(Object _value) {
									if ((Boolean) _value) {
										if (retryCallback != null) {
											// say to activity, that will trying
											// again
											activity.runOnUiThread(new Runnable() {
												@Override
												public void run() {
													retryCallback.finished(null);
												}
											});
										}

										asyncCall(activity, callback, retryCallback, failCallback, server, fn, parameters, cache_type, retryTimes,
												showRetryDialog);
									}
								}
							}, failCallback);
						}
					}
				}
			}
		}.start();
	}

	public static boolean isOnline(Activity activity) {
		ConnectivityManager cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnectedOrConnecting() && netInfo.isAvailable())
			return true;
		return false;
	}

	@SuppressWarnings("unused")
	private static HttpClient toSslClient(HttpClient client) {
		try {
			X509TrustManager tm = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
				}

				public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException {
				}

				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};
			SSLContext ctx = SSLContext.getInstance("TLS");
			ctx.init(null, new TrustManager[] { tm }, null);
			SSLSocketFactory ssf = new MySSLSocketFactory(ctx);
			ssf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			ClientConnectionManager ccm = client.getConnectionManager();
			SchemeRegistry sr = ccm.getSchemeRegistry();
			sr.register(new Scheme("https", ssf, 443));
			return new DefaultHttpClient(ccm, client.getParams());
		} catch (Exception ex) {
			return null;
		}
	}

	public static class MyHttpClient extends DefaultHttpClient {
		final Context context;

		public MyHttpClient(Context context) {
			this.context = context;
		}

		@Override
		protected ClientConnectionManager createClientConnectionManager() {
			SchemeRegistry registry = new SchemeRegistry();
			registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
			// Register for port 443 our SSLSocketFactory with our keystore
			// to the ConnectionManager
			registry.register(new Scheme("https", newSslSocketFactory(), 443));
			return new SingleClientConnManager(getParams(), registry);
		}

		private SSLSocketFactory newSslSocketFactory() {
			try {
				// Get an instance of the Bouncy Castle KeyStore format
				KeyStore trusted = KeyStore.getInstance("BKS");
				// Get the raw resource, which contains the keystore with
				// your trusted certificates (root and any intermediate certs)
				// Pass the keystore to the SSLSocketFactory. The factory is
				// responsible
				// for the verification of the server certificate.
				SSLSocketFactory sf = new SSLSocketFactory(trusted);
				// Hostname verification from certificate
				// http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d4e506
				sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
				return sf;
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		}
	}

	public static class MySSLSocketFactory extends SSLSocketFactory {
		SSLContext sslContext = SSLContext.getInstance("TLS");

		public MySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
			super(truststore);

			TrustManager tm = new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain, String authType) {
				}

				public void checkServerTrusted(X509Certificate[] chain, String authType) {
				}

				public X509Certificate[] getAcceptedIssuers() {
					return null;
				}
			};

			sslContext.init(null, new TrustManager[] { tm }, null);
		}

		public MySSLSocketFactory(SSLContext context) throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException, UnrecoverableKeyException {
			super(null);
			sslContext = context;
		}

		@Override
		public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
			return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
		}

		@Override
		public Socket createSocket() throws IOException {
			return sslContext.getSocketFactory().createSocket();
		}
	}
}
