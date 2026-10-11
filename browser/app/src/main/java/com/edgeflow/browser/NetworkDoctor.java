package com.edgeflow.browser;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

final class NetworkDoctor {
    static final class Snapshot {
        final boolean connected;
        final boolean validated;
        final boolean metered;
        final String transport;

        Snapshot(boolean connected, boolean validated, boolean metered, String transport) {
            this.connected = connected;
            this.validated = validated;
            this.metered = metered;
            this.transport = transport;
        }

        String shortLabel() {
            if (!connected) return "offline";
            if (!validated) return transport + "?";
            return transport;
        }
    }

    static Snapshot inspect(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return new Snapshot(false, false, false, "net");
            Network network = cm.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
            if (caps == null) return new Snapshot(false, false, cm.isActiveNetworkMetered(), "net");
            boolean connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            String transport = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "mobile"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN"
                    : caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "ethernet"
                    : "net";
            return new Snapshot(connected, validated, cm.isActiveNetworkMetered(), transport);
        } catch (Throwable ignored) {
            return new Snapshot(false, false, false, "net");
        }
    }

    private NetworkDoctor() {}
}
