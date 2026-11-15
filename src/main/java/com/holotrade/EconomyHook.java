package com.holotrade;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/** Puente a Vault por reflexion (no hace falta compilar contra Vault). */
public class EconomyHook {
    private Object eco;
    private Method has, withdraw, deposit, format, balance, success;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean setup() {
        try {
            Class<?> cls = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) cls);
            if (rsp == null) return false;
            eco = rsp.getProvider();
            has = cls.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = cls.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = cls.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            format = cls.getMethod("format", double.class);
            balance = cls.getMethod("getBalance", OfflinePlayer.class);
            Class<?> resp = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            success = resp.getMethod("transactionSuccess");
            return true;
        } catch (Throwable t) {
            eco = null;
            return false;
        }
    }

    public boolean available() {
        return eco != null || setup();
    }

    public boolean has(OfflinePlayer p, double amount) {
        if (!available()) return false;
        try {
            return (boolean) has.invoke(eco, p, amount);
        } catch (Throwable t) {
            return false;
        }
    }

    public double balance(OfflinePlayer p) {
        if (!available()) return 0;
        try {
            return ((Number) balance.invoke(eco, p)).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (!available()) return false;
        try {
            return (boolean) success.invoke(withdraw.invoke(eco, p, amount));
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        if (!available()) return false;
        try {
            return (boolean) success.invoke(deposit.invoke(eco, p, amount));
        } catch (Throwable t) {
            return false;
        }
    }

    public String format(double amount) {
        if (available()) {
            try {
                return (String) format.invoke(eco, amount);
            } catch (Throwable ignored) {
            }
        }
        return String.format("$%.2f", amount);
    }
}
