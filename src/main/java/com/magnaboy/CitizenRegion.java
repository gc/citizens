package com.magnaboy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.runelite.client.RuneLite;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

///The main list of all the citizens
public class CitizenRegion {
    public float Version = 0.8f;        //This is just in case we want to make any major changes to the files
    public int RegionId;
    public List<CitizenInfo> CitizenRoster = new ArrayList<>();
    //TODO: Implement Scenery Loading
    public  transient List<Scenery> SceneryList = new ArrayList<>();
    public transient List<Citizen> Citizens = new ArrayList<>();
    private String path;

    public CitizenRegion(int regionId)
    {
        this.RegionId = regionId;
    }

    public CitizenRegion SetPath(String path)
    {
        this.path = path;
        return this;
    }
    public void SaveRegion() throws IOException
    {
        SaveRegion(false);
    }

    public void SaveRegion(boolean prettyPrint) throws IOException
    {
        try {
            URL url = this.getClass().getResource("/RegionData/"+RegionId+".json");
            Writer wr = new BufferedWriter(new FileWriter(url.getPath()));
            GsonBuilder gb = new GsonBuilder();
            if (prettyPrint)
                gb.setPrettyPrinting();
            Gson gson = gb.create();
            gson.toJson(this, wr);
            wr.flush();
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
    public static CitizenRegion LoadRegion(int regionId, CitizensPlugin plugin)
    {
        InputStream inputStream = plugin.getClass().getResourceAsStream("/RegionData/"+regionId+".json");
        if(inputStream == null)
            return null;
        try (Reader reader = new BufferedReader(new InputStreamReader(inputStream)))
        {
            Gson gson = new Gson();
            CitizenRegion region = gson.fromJson(reader, CitizenRegion.class);
            region.Citizens = new ArrayList<>();
            for(CitizenInfo info : region.CitizenRoster)
            {
                Citizen c = new StationaryCitizen(plugin)
                        .setWorldLocation(info.worldLocation)
                        .setModelIDs(info.modelIds)
                        .setModelRecolors(info.modelRecolorFind, info.modelRecolorReplace)
                        .setIdleAnimation(info.idleAnimation)
                        .setName(info.name)
                        .setExamine(info.examineText)
                        .setRemarks(info.remarks)
                        .setScale(info.scale)
                        .setTranslate(info.translate)
                        .setBaseOrientation(info.baseOrientation);
                region.Citizens.add(c);
                System.out.println("Loaded::" + info.name);
            }
            return region;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
