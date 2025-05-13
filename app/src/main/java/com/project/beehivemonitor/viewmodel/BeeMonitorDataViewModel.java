package com.project.beehivemonitor.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.project.beehivemonitor.util.Event;

import java.util.Random;

public class BeeMonitorDataViewModel extends ViewModel {
    MutableLiveData<Event<String>> eventLiveData = new MutableLiveData<>();
    MutableLiveData<Event<Float>> temperatureLiveData = new MutableLiveData<>();
    MutableLiveData<Event<Float>> humidityLiveData = new MutableLiveData<>();

    public LiveData<Event<String>> getEventLiveData() {
        return eventLiveData;
    }

    public LiveData<Event<Float>> getHumidityLiveData() {
        return humidityLiveData;
    }

    public LiveData<Event<Float>> getTemperatureLiveData() {
        return temperatureLiveData;
    }

    Random random = new Random();
    public void postSampleData() {
        float tempMin = 25.0f;
        float tempMax = 30.0f;
        float randomTempFloat = tempMin + random.nextFloat() * (tempMax - tempMin);
        temperatureLiveData.postValue(new Event<>(randomTempFloat));

        float humidityMin = 28.0f;
        float humidityMax = 52.0f;
        float randomHumidityFloat = humidityMin + random.nextFloat() * (humidityMax - humidityMin);
        humidityLiveData.postValue(new Event<>(randomHumidityFloat));

        String event = "";
        switch(random.nextInt(3)) {
            case 0: {
                event = "NORMAL";
                break;
            }
            case 1: {
                event = "NO QUEEN";
                break;
            }
            case 2: {
                event = "SWARMING";
                break;
            }
        }
        eventLiveData.postValue(new Event<>(event));
    }
}
