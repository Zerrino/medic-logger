package fr.mediclogger.app;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Optional tips: consumable Google Play in-app products that unlock nothing and send no journal data.
 * Create three in-app products in Play Console (Monetize → Products → In-app products) with the IDs below.
 * Enable "multi-quantity" on the small one to let people choose their own amount in Google's payment sheet.
 */
final class TipJar implements com.android.billingclient.api.PurchasesUpdatedListener {
    enum Tip {
        SMALL("fr.mediclogger.app.tip.small","Small coffee"),
        MEDIUM("fr.mediclogger.app.tip.medium","Large coffee"),
        LARGE("fr.mediclogger.app.tip.large","Coffee and croissant");
        final String id,label;
        Tip(String id,String label){this.id=id;this.label=label;}
        static Tip of(String id){for(Tip tip:values())if(tip.id.equals(id))return tip;return null;}
    }
    enum Status { IDLE, PURCHASING, THANKED, PENDING, FAILED }

    private final Handler main=new Handler(Looper.getMainLooper());
    private final BillingClient client;
    private final Runnable onChange;
    private List<ProductDetails> products=Collections.emptyList();
    private boolean loaded,loading;
    private Status status=Status.IDLE;

    TipJar(Context context,Runnable onChange){
        this.onChange=onChange;
        client=BillingClient.newBuilder(context.getApplicationContext()).setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection().build();
    }
    List<ProductDetails> products(){return products;}
    boolean loaded(){return loaded;}
    Status status(){return status;}
    void clearStatus(){status=Status.IDLE;}

    /** Connects once per screen. A failed attempt stays failed until the person taps "Try again" (see retry). */
    void load(){
        if(loading||loaded)return;
        loading=true;
        if(client.isReady()){query();return;}
        try{
            client.startConnection(new BillingClientStateListener(){
                @Override public void onBillingSetupFinished(BillingResult result){
                    if(result.getResponseCode()==BillingClient.BillingResponseCode.OK){query();finishPending();}
                    else finishLoading(Collections.emptyList());
                }
                @Override public void onBillingServiceDisconnected(){}
            });
        }catch(RuntimeException unavailable){finishLoading(Collections.emptyList());}
    }
    void retry(){if(loading)return;loaded=false;load();}
    void close(){try{client.endConnection();}catch(RuntimeException ignored){}}

    private void finishLoading(List<ProductDetails> list){
        main.post(()->{
            List<ProductDetails> sorted=new ArrayList<>(list);sorted.sort((a,b)->Long.compare(micros(a),micros(b)));
            products=sorted;loaded=true;loading=false;onChange.run();
        });
    }
    private void query(){
        List<QueryProductDetailsParams.Product> wanted=new ArrayList<>();
        for(Tip tip:Tip.values())wanted.add(QueryProductDetailsParams.Product.newBuilder().setProductId(tip.id).setProductType(BillingClient.ProductType.INAPP).build());
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(wanted).build(),(result,details)->
            finishLoading(result.getResponseCode()==BillingClient.BillingResponseCode.OK?details.getProductDetailsList():Collections.emptyList()));
    }
    void buy(Activity activity,ProductDetails product){
        status=Status.PURCHASING;
        BillingResult result=client.launchBillingFlow(activity,BillingFlowParams.newBuilder().setProductDetailsParamsList(
            Collections.singletonList(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build())).build());
        if(result.getResponseCode()!=BillingClient.BillingResponseCode.OK)status=Status.FAILED;
        onChange.run();
    }
    @Override public void onPurchasesUpdated(BillingResult result,List<Purchase> purchases){
        main.post(()->{
            switch(result.getResponseCode()){
                case BillingClient.BillingResponseCode.OK->{if(purchases!=null)for(Purchase purchase:purchases)handle(purchase,true);}
                case BillingClient.BillingResponseCode.USER_CANCELED->status=Status.IDLE;
                default->status=Status.FAILED;
            }
            onChange.run();
        });
    }
    private void handle(Purchase purchase,boolean thank){
        if(purchase.getPurchaseState()==Purchase.PurchaseState.PURCHASED){
            // Consuming lets people give again and satisfies Play's requirement to acknowledge within three days.
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),(result,token)->{
                if(result.getResponseCode()==BillingClient.BillingResponseCode.OK&&thank)main.post(()->{status=Status.THANKED;onChange.run();});
            });
        }else if(purchase.getPurchaseState()==Purchase.PurchaseState.PENDING&&thank)status=Status.PENDING;
    }
    /** A payment confirmed after the app was closed is consumed on the next start. */
    private void finishPending(){
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),(result,list)->{
            for(Purchase purchase:list)handle(purchase,false);
        });
    }
    static long micros(ProductDetails product){
        List<ProductDetails.OneTimePurchaseOfferDetails> offers=product.getOneTimePurchaseOfferDetailsList();
        return offers==null||offers.isEmpty()?0:offers.get(0).getPriceAmountMicros();
    }
    static String price(ProductDetails product){
        List<ProductDetails.OneTimePurchaseOfferDetails> offers=product.getOneTimePurchaseOfferDetailsList();
        return offers==null||offers.isEmpty()?"":offers.get(0).getFormattedPrice();
    }
}
