package com.tn.wallet.ui.transactions;

import android.content.Context;
import android.content.Intent;
import android.databinding.Bindable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.ContextCompat;

import com.tn.wallet.R;
import com.tn.wallet.data.datamanagers.AddressBookManager;
import com.tn.wallet.data.datamanagers.TransactionListDataManager;
import com.tn.wallet.injection.Injector;
import com.tn.wallet.payload.MassTransferTransaction;
import com.tn.wallet.payload.PaymentTransaction;
import com.tn.wallet.payload.Transaction;
import com.tn.wallet.payload.TransferTransaction;
import com.tn.wallet.ui.base.BaseViewModel;
import com.tn.wallet.util.MoneyUtil;
import com.tn.wallet.util.PrefsUtil;
import com.tn.wallet.util.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

import static com.tn.wallet.ui.balance.TransactionsFragment.KEY_TRANSACTION_LIST_POSITION;

@SuppressWarnings("WeakerAccess")
public class MassTransferDetailViewModel extends BaseViewModel {

    private DataListener mDataListener;
    @Inject PrefsUtil mPrefsUtil;
    @Inject StringUtils mStringUtils;
    @Inject TransactionListDataManager mTransactionListDataManager;

    private Context context;

    @VisibleForTesting
    MassTransferTransaction mTransaction;

    private String walletName;

    public interface DataListener {

        Intent getPageIntent();

        void pageFinish();

    }

    public MassTransferDetailViewModel(Context context, DataListener listener) {
        this.context = context;
        Injector.getInstance().getDataManagerComponent().inject(this);
        mDataListener = listener;
    }

    @Override
    public void onViewReady() {
        if (mDataListener.getPageIntent() != null
                && mDataListener.getPageIntent().hasExtra(KEY_TRANSACTION_LIST_POSITION)) {
            int position = mDataListener.getPageIntent().getIntExtra(KEY_TRANSACTION_LIST_POSITION, -1);
            if (position == -1) {
                mDataListener.pageFinish();
            } else {
                mTransaction = (MassTransferTransaction) mTransactionListDataManager.getTransactionList().get(position);
            }
            walletName = mPrefsUtil.getValue(PrefsUtil.KEY_WALLET_NAME, "");
        } else {
            mDataListener.pageFinish();
        }
        updateUiFromTransaction();
    }


    @Bindable
    public String getTransactionType() {
        switch (mTransaction.getDirection()) {
            case Transaction.RECEIVED:
                return mStringUtils.getString(R.string.RECEIVED);
            case Transaction.SENT:
                return mStringUtils.getString(R.string.SENT);
        }
        return null;
    }

    private void updateUiFromTransaction() {
    }

    @Bindable
    public String getTransactionAmount() {
        return MoneyUtil.getTextStripZeros(mTransaction.getSum(), mTransaction.getDecimals()) + " " + mTransaction.getAssetName();
    }


    @Bindable
    public int getTransactionColor() {
        switch (mTransaction.getDirection()) {
            case Transaction.RECEIVED:
                return ContextCompat.getColor(context, R.color.blockchain_receive_green);
            case Transaction.SENT:
                return ContextCompat.getColor(context, R.color.blockchain_send_red);
        }
        return R.color.blockchain_transfer_blue;
    }

    @Bindable
    public String getTransactionFee() {
        return mStringUtils.getString(R.string.transaction_detail_fee) +
                MoneyUtil.getWavesStripZeros(mTransaction.fee) + " WAVES";
    }

    @Bindable
    public String getConfirmationStatus() {
        if (mTransaction.isPending) return mStringUtils.getString(R.string.transaction_detail_pending);
        else return mStringUtils.getString(R.string.transaction_detail_confirmed);
    }

    @Bindable
    public String getAssetName() {
        return mTransaction.getAssetName();
    }

    @Bindable
    public String getAssetId() {
        return mTransaction.assetId;
    }

    @Bindable
    public String getToAddressLabel() {
        if (mTransaction.getDirection() == Transaction.SENT) {
            return AddressBookManager.get().addressToLabel(mTransaction.getRecipient());
        } else {
            return walletName;
        }
    }

    @Bindable
    public String getToAddress() {
        return mTransaction.getRecipient();
    }

    @Bindable
    public String getFromAddressLabel() {
        if (mTransaction.getDirection() == Transaction.RECEIVED) {
            return AddressBookManager.get().addressToLabel(mTransaction.sender);
        } else {
            return walletName;
        }
    }

    @Bindable
    public String getFromAddress() {
        return mTransaction.sender;
    }

    @Bindable
    public String getTransactionDate() {
        Date date = new Date(mTransaction.timestamp);
        DateFormat dateFormat = SimpleDateFormat.getDateInstance(DateFormat.LONG);
        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        String dateText = dateFormat.format(date);
        String timeText = timeFormat.format(date);

        return dateText + " @ " + timeText;
    }

    @Bindable
    public String getAttachment() {
        return StringUtils.fromBase58Utf8(mTransaction.attachment);
    }

}
