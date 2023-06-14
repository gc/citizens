package com.magnaboy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

///The main list of all the citizens
public class CitizenRegion {
    public static final String resourcesDir = new StringBuilder().append(System.getProperty("user.dir"))
                                                                 .append(File.separator)
                                                                 .append("resources")
                                                                 .append(File.separator)
                                                                 .append("regionData")
                                                                 .append(File.separator).toString();
    public float version = 0.8f;        //This is just in case we want to make any major changes to the files
    public int RegionId;
    public List<CitizenInfo> CitizenList = new ArrayList<>();
    public transient List<Citizen> Citizens = new ArrayList<>();

    public CitizenRegion(int regionId, List<Citizen> citizens)
    {
        this.RegionId = regionId;
        for(Citizen citizen : citizens)
        {
            //Java, why no object initialization shorthand?
            CitizenInfo info = new CitizenInfo();
            info.name = citizen.name;
            info.examineText = citizen.examine;
            info.remarks = citizen.remarks;
            info.modelRecolorFind = citizen.getRecolorsToFind();
            info.modelRecolorReplace = citizen.getRecolorsToReplace();
            info.worldPoint = citizen.location;
            info.modelIds = citizen.getModelIDs();
            info.idleAnimation = citizen.idleAnimationId;
            info.moveAnimation = citizen.movingAnimationId;
            info.baseOrientation = citizen.baseOrientation;
            info.scale = citizen.scale;
            info.translate = citizen.translate;
            this.CitizenList.add(info);
        }
    }

    public void SaveRegion() throws IOException
    {
        SaveRegion(false);
    }

    public void SaveRegion(boolean prettyPrint) throws IOException
    {
        try {
            Files.createDirectories(Paths.get(resourcesDir));
            Writer wr = new FileWriter(resourcesDir+RegionId+".json");
            GsonBuilder gb = new GsonBuilder();
            if(prettyPrint)
                gb.setPrettyPrinting();
            Gson gson =gb.create();
            gson.toJson(this, wr);
            wr.flush();
        } catch (IOException e) {
            throw new IOException(e);
        }
    }
    public static CitizenRegion LoadRegion(int regionId, CitizensPlugin plugin) throws IOException
    {
        try {
            Gson gson = new Gson();
            if(!new File(resourcesDir+regionId+".json").exists())
                return null;
            CitizenRegion cr = gson.fromJson(new FileReader(resourcesDir+regionId+".json"), CitizenRegion.class);
            cr.Citizens = new ArrayList<>();
            for(CitizenInfo info : cr.CitizenList)
            {
                Citizen c = new StationaryCitizen(plugin)
                        .setLocation(info.worldPoint)
                        .setModelIDs(info.modelIds)
                        .setModelRecolors(info.modelRecolorFind, info.modelRecolorReplace)
                        .setIdleAnimation(info.idleAnimation)
                        .setName(info.name)
                        .setExamine(info.examineText)
                        .setRemarks(info.remarks)
                        .setBaseOrientation(info.baseOrientation)
                        .setScale(info.scale);
                c.spawn();
                cr.Citizens.add(c);
                System.out.println("Loaded::" + info.name);
            }
            return cr;
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

}
