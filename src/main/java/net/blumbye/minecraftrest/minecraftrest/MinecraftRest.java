package net.blumbye.minecraftrest.minecraftrest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.sun.management.OperatingSystemMXBean;

import static spark.Spark.*;

public final class MinecraftRest extends JavaPlugin {

    private final int port = 3050;
    private String cStatus;
    private String cPlayers;
    private String cLastonline;
    private Date lastUpdate;
    private OperatingSystemMXBean osBean;

    private static String dataToJson(Object data) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            StringWriter sw = new StringWriter();
            mapper.writeValue(sw, data);
            return sw.toString();
        } catch (IOException e){
            throw new RuntimeException("IOException from a StringWriter?");
        }
    }

    @Override
    public void onEnable() {
        osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

        // Start web server
        port(port);

        enableCORS();

        get("/status", (req, res) -> getStatus());

        get("/players", (req, res) -> getPlayers());

        get("/lastonline", (req, res) -> getLastonline());
    }

    private String getStatus() {
        if (lastUpdate == null || cStatus == null || lastUpdate.after(new Date((new Date().getTime() - 1000)))) {
            Pattern versionPattern = Pattern.compile(".*?-([1-9]\\w+).*MC: (.*?)\\)");
            Matcher versionMatcher = versionPattern.matcher(Bukkit.getVersion());
            versionMatcher.find();
            World overworld = getServer().getWorlds().get(0);

            Status status = new Status();
            status.setOnline(true);
            status.setPaperVersion(versionMatcher.group(1));
            status.setMinecraftVersion(versionMatcher.group(2));
            status.setStartTime(ManagementFactory.getRuntimeMXBean().getStartTime());
            status.setPlayers(getServer().getOnlinePlayers().size());
            status.setTps(getServer().getTPS());
            status.setCpuUsage(osBean.getSystemCpuLoad());
            status.setMemoryFree(osBean.getFreePhysicalMemorySize());
            status.setMemoryMax(osBean.getTotalPhysicalMemorySize());
            status.setWeather(overworld.isThundering() ? "Thunder" : overworld.hasStorm() ? "Rain" : "Clear");
            status.setTimeOfDay(overworld.getTime());
            status.setTotalDays((int) (overworld.getFullTime() / 24000));

            cStatus = dataToJson(status);
            return cStatus;
        } else {
            return cStatus;
        }
    }

    private String getPlayers() {
        if (lastUpdate == null || cPlayers == null || lastUpdate.after(new Date((new Date().getTime() - 1000)))) {
            cPlayers = dataToJson(getServer().getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            return cPlayers;
        } else {
            return cPlayers;
        }
    }

    private String getLastonline() {
        if (lastUpdate == null || cLastonline == null || lastUpdate.after(new Date((new Date().getTime() - 1000)))) {
            List<OfflinePlayer> players = new ArrayList<>(Arrays.asList(getServer().getOfflinePlayers()));
            players.removeIf(p -> p.getName() == null || p.isOnline());

            // Sort the players
            class sortBySeen implements Comparator<OfflinePlayer> {
                public int compare(OfflinePlayer a, OfflinePlayer b) {
                    if (a.isOnline()) {
                        return b.isOnline() ? 0 : -1;
                    } else if (b.isOnline()) {
                        return 1;
                    }
                    return Long.compare(b.getLastSeen(), a.getLastSeen());
                }
            }
            players.sort(new sortBySeen());

            List<LastOnlinePlayer> formattedPlayers = players.stream().map(p -> new LastOnlinePlayer(p.getName(), p.getLastSeen())).collect(Collectors.toList());
            cLastonline = dataToJson(formattedPlayers);
            return cLastonline;
        } else {
            return cLastonline;
        }
    }

    // Enables CORS on requests. This method is an initialization method and should be called once.
    private static void enableCORS() {

        options("/*", (request, response) -> {

            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Request-Method", "*");
            response.header("Access-Control-Allow-Headers", "*");
            // Note: this may or may not be necessary in your particular application
            response.type("application/json");
        });
    }

    @Override
    public void onDisable() {
        stop();
    }
}

class LastOnlinePlayer {
    private String name;
    private long lastSeen;

    public LastOnlinePlayer(String name, long lastSeen) {
        this.name = name;
        this.lastSeen = lastSeen;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public String toString() {
        return "LastOnlinePlayer{" + "name='" + name + '\'' + ", lastSeen=" + lastSeen + '}';
    }
}

class Status {
    private boolean online;
    private String paperVersion;
    private String minecraftVersion;
    private long startTime;
    private int players;
    private double cpuUsage;
    private long memoryFree; // In bytes
    private long memoryMax; // In bytes
    private String weather;
    private long timeOfDay;
    private int totalDays;

    public double[] getTps() {
        return tps;
    }

    public void setTps(double[] tps) {
        this.tps = tps;
    }

    private double[] tps;

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getPaperVersion() {
        return paperVersion;
    }

    public void setPaperVersion(String paperVersion) {
        this.paperVersion = paperVersion;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public void setMinecraftVersion(String minecraftVersion) {
        this.minecraftVersion = minecraftVersion;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public int getPlayers() {
        return players;
    }

    public void setPlayers(int players) {
        this.players = players;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public long getMemoryFree() {
        return memoryFree;
    }

    public void setMemoryFree(long memoryFree) {
        this.memoryFree = memoryFree;
    }

    public long getMemoryMax() {
        return memoryMax;
    }

    public void setMemoryMax(long memoryMax) {
        this.memoryMax = memoryMax;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public long getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(long timeOfDay) {
        this.timeOfDay = timeOfDay;
    }

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    @Override
    public String toString() {
        return "Status{" + "online=" + online + ", paperVersion='" + paperVersion + '\'' + ", minecraftVersion='" + minecraftVersion + '\'' + ", startTime=" + startTime + ", players=" + players + ", cpuUsage=" + cpuUsage + ", memoryFree=" + memoryFree + ", memoryMax=" + memoryMax + ", weather='" + weather + '\'' + ", timeOfDay='" + timeOfDay + '\'' + ", totalDays=" + totalDays + ", tps=" + Arrays.toString(tps) + '}';
    }
}