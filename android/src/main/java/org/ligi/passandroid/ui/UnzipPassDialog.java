package org.ligi.passandroid.ui;

import android.app.Activity;
import android.app.ProgressDialog;
import android.support.v7.app.AlertDialog;
import org.ligi.axt.listeners.ActivityFinishingOnClickListener;
import org.ligi.passandroid.R;
import org.ligi.passandroid.model.InputStreamWithSource;
import org.ligi.passandroid.model.PassStore;
import static org.ligi.passandroid.ui.UnzipPassController.FailCallback;
import static org.ligi.passandroid.ui.UnzipPassController.InputStreamUnzipControllerSpec;
import static org.ligi.passandroid.ui.UnzipPassController.SuccessCallback;

public class UnzipPassDialog {

    static void displayError(final Activity activity, final String title, final String err) {
        new AlertDialog.Builder(activity).setTitle(title)
                                         .setMessage(err)
                                         .setPositiveButton(android.R.string.ok, new ActivityFinishingOnClickListener(activity))
                                         .show();
    }

    interface FinishCallback {
        void call(String path);
    }

    public static void show(final InputStreamWithSource ins,
                            final Activity activity,
                            final PassStore passStore,
                            final FinishCallback callAfterFinishOnUIThread) {
        if (activity.isFinishing()) {
            return; // no need to act any more ..
        }

        final ProgressDialog dialog = ProgressDialog.show(activity,
                                                          activity.getString(R.string.unzip_pass_dialog_title),
                                                          activity.getString(R.string.unzip_pass_dialog_message),
                                                          true);
        dialog.setCancelable(false);

        class AlertDialogUpdater implements Runnable {

            private final FinishCallback call_after_finish;

            AlertDialogUpdater(FinishCallback call_after_finish) {
                this.call_after_finish = call_after_finish;
            }

            public void run() {
                final InputStreamUnzipControllerSpec spec = new InputStreamUnzipControllerSpec(ins, activity, passStore, new SuccessCallback() {

                    @Override
                    public void call(final String uuid) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!prepareResult(activity, dialog)) {
                                    return;
                                }

                                call_after_finish.call(uuid);
                            }
                        });
                    }
                }, new FailCallback() {
                    @Override
                    public void fail(final String reason) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (!prepareResult(activity, dialog)) {
                                    return;
                                }

                                displayError(activity, activity.getString(R.string.invalid_passbook_title), reason);
                            }
                        });
                    }
                });
                UnzipPassController.INSTANCE.processInputStream(spec);
            }
        }

        final AlertDialogUpdater alertDialogUpdater = new AlertDialogUpdater(callAfterFinishOnUIThread);
        new Thread(alertDialogUpdater).start();

    }

    private static boolean prepareResult(final Activity activity, final ProgressDialog dialog) {
        if (activity.isFinishing()) {
            return false;
        }

        if (dialog.isShowing()) {
            try {
                dialog.dismiss();
                return true;
            } catch (IllegalArgumentException ignored) {
                // Would love a better option - searched a long time - found nothing - and this is better than a crash
            }
        }
        return false;
    }

}
