package ru.clouddonate.shop;

import ru.clouddonate.converters.impl.JsonConverterImpl;
import ru.clouddonate.multiplatform.CommandExecuteService;
import ru.clouddonate.result.GetResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;

public abstract class Shop {
    private final CommandExecuteService service;
    private ShopRecord info;
    private Thread thread;
    private boolean started = false;
    private boolean debug = false;
    private final JsonConverterImpl jsonConverter = new JsonConverterImpl();
    private final Logger logger = Logger.getLogger("CloudPayments (Shop class)");

    public Shop(final ShopRecord info, final long updateDelay, final CommandExecuteService service) {
        this.setInfo(info, updateDelay);
        this.service = service;
    }

    public void startReceivePurchases() {
        if (started) {
            logger.severe("The cloudpayments already receiving purchases");
            return;
        }

        this.started = true;
        this.thread.start();
    }

    public void stopReceivePurchases() {
        if (!started) {
            logger.severe("The cloudpayments not receiving purchases");
            return;
        }

        this.started = false;
        this.thread.interrupt();
    }

    public abstract void onApprove(GetResult result);

    /*
        Геттеры и сеттеры
     */

    public CommandExecuteService getService() {
        return service;
    }

    public Thread getThread() {
        return thread;
    }

    public boolean isStarted() {
        return started;
    }

    public Logger getLogger() {
        return logger;
    }

    public ShopRecord getInfo() {
        return info;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setInfo(ShopRecord info, long updateDelay) {
        this.info = info;
        this.thread = new Thread(() -> {
            final String approveUrl = String.format("https://api.cdonate.ru/api/v1/shops/%s/purchases/{purchase_id}/approve", info.getShopId());
            final String getUrl = String.format("https://api.cdonate.ru/api/v1/shops/%s/purchases/pending?server_id=%s", info.getShopId(), info.getServerId());

            while (true) {
                if (!started) return;
                try {
                    HttpURLConnection getConnection = (HttpURLConnection) new URL(getUrl).openConnection();
                    getConnection.setRequestMethod("GET");
                    getConnection.setRequestProperty("X-Shop-Key", info.getShopKey());
                    getConnection.setRequestProperty("Content-Type", "application/json");
                    getConnection.setConnectTimeout(5000);
                    getConnection.setReadTimeout(5000);

                    int responseCode = getConnection.getResponseCode();
                    if (responseCode != 200 && responseCode != 204) {
                        if (this.isDebug())
                            this.logger.severe("Сервер отдал не успешный ответный код: " + responseCode);
                        return;
                    }

                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(getConnection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) response.append(line);
                    }

                    GetResult[] results = this.jsonConverter.gson.fromJson(response.toString(), GetResult[].class);
                    if (results == null) {
                        if (this.isDebug())
                            this.logger.severe("Сервер отдал не JSON-ответ. Ответ от сервера: " + response);
                        return;
                    }

                    if (this.isDebug())
                        this.logger.info("Получил " + results.length + " ответ(-ов) от сервера.");

                    Arrays.asList(results).forEach(result -> process(result, approveUrl));
                    Thread.sleep(updateDelay);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void process(final GetResult result, final String approveUrl) {
        HttpURLConnection postConnection;
        try {
            postConnection = (HttpURLConnection)new URL(approveUrl.replaceAll("\\{purchase_id}", String.valueOf(result.getId()))).openConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            postConnection.setRequestMethod("POST");
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        }
        postConnection.setRequestProperty("X-Shop-Key", this.info.getShopKey());
        postConnection.setRequestProperty("Content-Type", "application/json");
        postConnection.setDoOutput(true);
        postConnection.setConnectTimeout(5000);
        postConnection.setReadTimeout(5000);
        try (OutputStream os = postConnection.getOutputStream();){
            byte[] input = "{}".getBytes(StandardCharsets.UTF_8);
            try {
                os.write(input, 0, input.length);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.onApprove(result);
    }
}
