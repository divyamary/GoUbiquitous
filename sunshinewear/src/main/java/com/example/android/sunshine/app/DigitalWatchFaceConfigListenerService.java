/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine.app;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A {@link WearableListenerService} listening for {@link DigitalWatchFaceService} config messages
 * and updating the config {@link com.google.android.gms.wearable.DataItem} accordingly.
 */
public class DigitalWatchFaceConfigListenerService extends WearableListenerService {
    private static final String TAG = "DigitalListenerService";
    private static final String DATA_ITEM_RECEIVED_PATH = "/weather";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils
                .freezeIterable(dataEvents);
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent event : events) {
            PutDataMapRequest putDataMapRequest =
                    PutDataMapRequest.createFromDataMapItem(DataMapItem.fromDataItem(event.getDataItem()));

            String path = event.getDataItem().getUri().getPath();
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                if (DATA_ITEM_RECEIVED_PATH.equals(path)) {
                    DataMap dataMap = putDataMapRequest.getDataMap();
                    double high = dataMap.getDouble("high");
                    Log.d(TAG, "DataEvent.high:" + String.valueOf(high));
                } else if (event.getType() == DataEvent.TYPE_DELETED) {
                    Log.d(TAG, "DataEvent.TYPE_DELETED");
                }
                Uri uri = event.getDataItem().getUri();
                // Get the node id from the host value of the URI
                String nodeId = uri.getHost();
                // Set the data of the message to be the bytes of the URI
                byte[] payload = uri.toString().getBytes();
                // Send the RPC
                Wearable.MessageApi.sendMessage(googleApiClient, nodeId,
                        DATA_ITEM_RECEIVED_PATH, payload);
            }
        }
    }
}
