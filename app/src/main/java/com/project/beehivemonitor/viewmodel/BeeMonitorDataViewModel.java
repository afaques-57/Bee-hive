package com.project.beehivemonitor.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.project.beehivemonitor.model.Event;
import com.project.beehivemonitor.util.BluetoothOperations;
import com.project.beehivemonitor.util.Logger;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class BeeMonitorDataViewModel extends ViewModel {
    private static final String DATA_NOTIFICATION_UUID = "0000180f-0000-1000-8000-00805f9b34fb";

    MutableLiveData<Event<String>> eventLiveData = new MutableLiveData<>();
    MutableLiveData<Event<Float>> temperatureLiveData = new MutableLiveData<>();
    MutableLiveData<Event<Float>> humidityLiveData = new MutableLiveData<>();

    private final BluetoothOperations.DataCallback dataCallback = new BluetoothOperations.DataCallback() {
        @Override
        public void onCharacteristicChanged(byte[] value) {
            String parsedValue = new String(value, StandardCharsets.UTF_8);
            Logger.info("onCharacteristicChanged - parsedValue: " + parsedValue);
            try {
                JSONObject jsonObject = new JSONObject(parsedValue);
                Logger.info("onCharacteristicChanged - jsonObject: " + jsonObject);

                String eventKey = "event";
                String temperatureKey = "temperature";
                String humidityKey = "humidity";

                if (jsonObject.has(eventKey)) {
                    String event = jsonObject.getString(eventKey);
                    eventLiveData.postValue(new Event<>(event));
                }

                if (jsonObject.has(temperatureKey)) {
                    float temperature = (float) jsonObject.getDouble(temperatureKey);
                    temperatureLiveData.postValue(new Event<>(temperature));
                }

                if (jsonObject.has(humidityKey)) {
                    float humidity = (float) jsonObject.getDouble(humidityKey);
                    humidityLiveData.postValue(new Event<>(humidity));
                }
            } catch (JSONException e) {
                Logger.error("onCharacteristicChanged - JSONException: " + e.getMessage());
                e.printStackTrace();
            }
        }

        @Override
        public void onCharacteristicRead(byte[] value) {
        }
    };

    public BeeMonitorDataViewModel() {
        BluetoothOperations.addDataCallback(DATA_NOTIFICATION_UUID, dataCallback);
    }

    public LiveData<Event<String>> getEventLiveData() {
        return eventLiveData;
    }

    public LiveData<Event<Float>> getHumidityLiveData() {
        return humidityLiveData;
    }

    public LiveData<Event<Float>> getTemperatureLiveData() {
        return temperatureLiveData;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        BluetoothOperations.removeDataCallback(DATA_NOTIFICATION_UUID, dataCallback);
    }
}
