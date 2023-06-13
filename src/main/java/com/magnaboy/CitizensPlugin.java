package com.magnaboy;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.magnaboy.Util.getRandom;

@Slf4j
@PluginDescriptor(name = "Citizens", description = "Adds citizens to help bring life to the world")
public class CitizensPlugin extends Plugin {
    @Inject
    public Client client;

    @Inject
    private CitizensConfig config;

    @Provides
    CitizensConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(CitizensConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    ChatMessageManager chatMessageManager;

    @Inject
    private CitizensOverlay citizensOverlay;

    @Inject
    public ClientThread clientThread;

    public List<Animation> animationPoses = new ArrayList<Animation>();
    public AnimationID[] randomIdleActionAnimationIds = {AnimationID.Flex};
    public List<Citizen> citizens = new ArrayList<Citizen>();
    public List<Scenery> scenery = new ArrayList<Scenery>();
    public List<List<? extends Entity>> entityCollection = new ArrayList<>();

    public CitizenPanel panel;

    public Animation getAnimation(AnimationID animID) {
        Animation anim = animationPoses.stream().filter(c -> c.getId() == animID.getId()).findFirst().orElse(null);
        if (anim == null) {
            throw new IllegalStateException("Tried to get non-existant anim: " + animID);
        }
        return anim;
    }

    public boolean entitiesAreReady = false;

    public boolean isReady() {
        return entitiesAreReady && client.getLocalPlayer() != null;
    }

    @Inject
    private ClientToolbar clientToolbar;

    @Override
    protected void startUp() {
        // Init config panel
        panel = injector.getInstance(CitizenPanel.class);
        panel.init(this);

        // Add to sidebar
        final BufferedImage icon = ImageUtil.loadImageResource(CitizensPlugin.class, "/citizens_icon.png");
        NavigationButton navButton = NavigationButton.builder()
                                                     .tooltip("Citizens")
                                                     .icon(icon)
                                                     .priority(7)
                                                     .panel(panel)
                                                     .build();
        clientToolbar.addNavigation(navButton);

        // Add overlay
        overlayManager.add(citizensOverlay);

        for (AnimationID animId : randomIdleActionAnimationIds) {
            loadAnimation(animId);
        }

        for (AnimationID idList : AnimationID.values()) {
            loadAnimation(idList);
        }

        citizens.add(
                new WanderingCitizen(this)
                        .setBoundingBox(new WorldPoint(3209, 3432, 0), new WorldPoint(3215, 3437, 0))
                        .setModelIDs(new int[]{38135})
                        .setIdleAnimation(AnimationID.HumanIdle)
                        .setName("Emme")
                        .setExamine("Gmme!").setRemarks(new String[]{"Good morning!"}));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3169, 3489, 0))
                .setModelIDs(new int[]{217,
                        305,
                        170,
                        176,
                        274,
                        7121,
                        246
                })
                .setIdleAnimation(AnimationID.FireCook)
                .setName("Richard")
                .setExamine("I wonder what he's cooking.").setRemarks(new String[]{"We need to cook!"})
                .addExtraObject(new ExtraObject(this).setWorldLocation(new WorldPoint(3169, 3488, 0))
                                                     .setModelIDs(new int[]{2260, 3818})
                                                     .setIdleAnimation(AnimationID.FireIdle))
        );

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3227, 3459, 0))
                .setModelIDs(new int[]{217,
                        8798,
                        390,
                        7366,
                        471,
                        4392,
                        348,
                        353,
                        46747,
                        437
                })
                .setModelRecolors(new int[]{4550,
                                25238,
                                6798,
                                9096,
                                54397,
                                8741},
                        new int[]{4562,
                                5400,
                                7465,
                                5400,
                                8497,
                                12700})
                .setIdleAnimation(AnimationID.FireCook)
                .setName("Lily")
                .setExamine("Farming... such a peaceful life.").setRemarks(new String[]{"I thought I saw a " +
                        "Tangleroot..."})
                .setBaseOrientation(CardinalDirection.East));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3224, 3437, 0))
                .setModelIDs(new int[]{15103,
                        217,
                        248,
                        18546,
                        10980,
                        177,
                        18541,
                        21812,
                        19947
                })
                .setModelRecolors(new int[]{111,
                                8741,
                                8860,
                                25238,
                                947},
                        new int[]{24,
                                803,
                                922,
                                10409,
                                803})
                .setIdleAnimation(AnimationID.Woodcutting)
                .setName("Benny")
                .setExamine("Chop chop chop!").setRemarks(new String[]{"Chop chop!"})
                .setBaseOrientation(CardinalDirection.South));


        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3191, 3425, 0))
                .setModelIDs(new int[]{
                        217,
                        295,
                        150,
                        3779,
                        246
                })
                .setIdleAnimation(AnimationID.HalfLayingDown)
                .setName("Benny")
                .setExamine("Chop chop chop!").setRemarks(new String[]{"Chop chop!"})
                .setBaseOrientation(CardinalDirection.East)
                .addExtraObject(new ExtraObject(this).setWorldLocation(new WorldPoint(3191, 3424, 0))
                                                     .setModelIDs(new int[]{2548})));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3197, 3405, 0))
                .setModelIDs(new int[]{
                        9452,
                        9619,
                        246,
                        9622,
                        163,
                        176,
                        4226,
                        4218
                })
                .setModelRecolors(new int[]{
                                6798,
                                43072,
                                8741,
                                25238,
                                6587,
                                7281
                        },
                        new int[]{
                                4329,
                                4550,
                                10349,
                                6689,
                                5400,
                                5301
                        })
                .setIdleAnimation(AnimationID.HerbloreMix)
                .setName("Assistant Apothecary")
                .setExamine("Chop chop chop!").setRemarks(new String[]{"Chop chop!"})
                .setBaseOrientation(CardinalDirection.East)
                .setRandomAnimations(new AnimationID[]{AnimationID.Grabbing, AnimationID.Eat,
                        AnimationID.FurnaceSmelt, AnimationID.Think, AnimationID.SlapHead, AnimationID.Yawn}));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3216, 3402, 0))
                .setModelIDs(new int[]{
                        6364,
                        215,
                        244,
                        246,
                        292,
                        4391,
                        151,
                        10218,
                        271,
                        185
                })
                .setModelRecolors(
                        new int[]{
                                8741,
                                25238,
                                4550
                        },
                        new int[]{
                                6447,
                                6932,
                                4541
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setName("Alexander")
                .setExamine("Looks like a nice chap.")
                .setRemarks(new String[]{"Oi!"})
                .setBaseOrientation(CardinalDirection.North)
                .addExtraObject(
                        new ExtraObject(this)
                                .setWorldLocation(new WorldPoint(3216, 3403, 0))
                                .setModelIDs(new int[]{2491})
                                .setTranslate(0, 0.7f, 0))
        );

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3217, 3404, 0))
                .setModelIDs(new int[]{
                        26120,
                        26130,
                        26114,
                        26125,
                        26119
                })
                .setIdleAnimation(AnimationID.Sitting)
                .setName("Darren")
                .setExamine("He has a nice moustache.")
                .setRemarks(new String[]{"We need some more beers ova ere!"})
                .setBaseOrientation(CardinalDirection.West)
                .addExtraObject(
                        new ExtraObject(this)
                                .setWorldLocation(new WorldPoint(3216, 3404, 0))
                                .setModelIDs(new int[]{2491})
                                .setTranslate(0, 0.7f, 0)
                ));
        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3215, 3407, 0))
                .setModelIDs(new int[]{
                        391,
                        364,
                        456,
                        348,
                        353,
                        431,
                        358
                })
                .setModelRecolors(
                        new int[]{
                                4550,
                                8741,
                                25238,
                                59515
                        },
                        new int[]{
                                6705,
                                127,
                                127,
                                127
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setName("Afrah")
                .setExamine("She looks like she's from Al-Kharid.")
                .setRemarks(new String[]{"We need some more beers ova ere!"})
                .setBaseOrientation(CardinalDirection.East)
                .addExtraObject(
                        new ExtraObject(this)
                                .setWorldLocation(new WorldPoint(3216, 3407, 0))
                                .setModelIDs(new int[]{2822})
                                .setTranslate(0, 0.7f, 0)));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3217, 3408, 0))
                .setModelIDs(new int[]{
                        6086,
                        249,
                        292,
                        170,
                        254,
                        185,
                        176
                })
                .setModelRecolors(
                        new int[]{
                                8741,
                                25238,
                                4550
                        },
                        new int[]{
                                10351,
                                10351,
                                5555
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setName("Ali")
                .setExamine("She looks like she's from Al-Kharid.")
                .setRemarks(new String[]{"We need some more beers ova ere!"})
                .setBaseOrientation(CardinalDirection.South)
                .addExtraObject(
                        new ExtraObject(this)
                                .setWorldLocation(new WorldPoint(3216, 3407, 0))
                                .setModelIDs(new int[]{2822})
                                .setTranslate(0, 0.7f, 0)));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3238, 3425, 0))
                .setModelIDs(new int[]{
                        235,
                        248,
                        292,
                        10980,
                        8918,
                        4045,
                        271,
                        181
                })
                .setModelRecolors(
                        new int[]{
                                25238,
                                8741,
                                6798,
                                4550,
                                4626,
                                22177
                        },
                        new int[]{
                                10520,
                                8493,
                                7081,
                                3408,
                                2576,
                                908
                        }
                )
                .setIdleAnimation(AnimationID.Fletching)
                .setName("Fletching apprentice")
                .setExamine("She looks like she's from Al-Kharid.")
                .setRemarks(new String[]{"We need some more beers ova ere!"})
                .setBaseOrientation(CardinalDirection.South));

        // TODO: would be nice to script him to use the several things in the workshop
        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3227, 3436, 0))
                .setModelIDs(new int[]{
                        215,
                        246,
                        292,
                        4391,
                        151,
                        179,
                        274,
                        185,
                        491
                })
                .setModelRecolors(
                        new int[]{
                                10004,
                                8084,
                                8741,
                                25238,
                                7719,
                                4626
                        },
                        new int[]{
                                41282,
                                41271,
                                127,
                                41490,
                                41418,
                                43150
                        }
                )
                .setIdleAnimation(AnimationID.AnvilBang)
                .setName("Smithing apprentice")
                .setExamine("She looks like she's from Al-Kharid.")
                .setRemarks(new String[]{"Ouch!"})
                .setBaseOrientation(CardinalDirection.East));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3228, 3407, 0))
                .setModelIDs(new int[]{
                        215,
                        246,
                        292,
                        151,
                        10218,
                        254,
                        181
                })
                .setModelRecolors(
                        new int[]{
                                8741,
                                25238
                        },
                        new int[]{
                                21541,
                                7331
                        }
                )
                .setIdleAnimation(AnimationID.HumanIdle)
                .setName("Prisoner")
                .setExamine("I wonder what he did wrong.")
                .setRemarks(new String[]{"Help me!"})
                .setBaseOrientation(CardinalDirection.North));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3230, 3407, 0))
                .setModelIDs(new int[]{
                        217,
                        246,
                        25676,
                        14373,
                        10218,
                        12138,
                        181
                })
                .setIdleAnimation(AnimationID.Crying)
                .setName("Prisoner")
                .setExamine("He's not having a good day.")
                .setRemarks(new String[]{"Woe is me..."})
                .setBaseOrientation(CardinalDirection.North));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3204, 3386, 0))
                .setModelIDs(new int[]{
                        215,
                        228,
                        246,
                        292,
                        326,
                        170,
                        179,
                        254,
                        185,
                        317
                })
                .setModelRecolors(
                        new int[]{
                                25238,
                                8741
                        },
                        new int[]{
                                3486,
                                8728
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setName("Thief")
                .setExamine("TODO")
                .setRemarks(new String[]{"TODO"})
                .setBaseOrientation(CardinalDirection.West));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3203, 3387, 0))
                .setModelIDs(new int[]{
                        208,
                        10304,
                        25675,
                        317,
                        25643,
                        25652,
                        25668,
                        25650
                })
                .setModelRecolors(
                        new int[]{
                                7952,
                                8741
                        },
                        new int[]{
                                38156,
                                38160
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setName("Thief")
                .setExamine("TODO")
                .setRemarks(new String[]{"TODO"})
                .setBaseOrientation(CardinalDirection.South)
                .addExtraObject(new ExtraObject(this)
                        .setModelIDs(new int[]{37201})
                        .setWorldLocation(new WorldPoint(3203, 3386, 0))
                        .setTranslate(0, 0.7f, 0)));

        citizens.add(new WanderingCitizen(this)
                .setBoundingBox(new WorldPoint(3205, 3384, 0), new WorldPoint(3206, 3389, 0))
                .setModelIDs(new int[]{
                        215,
                        246,
                        292,
                        151,
                        179,
                        254,
                        185,
                        320,
                        18956
                })
                .setModelRecolors(
                        new int[]{
                                25238,
                                8741
                        },
                        new int[]{
                                47399,
                                6930
                        }
                )
                .setIdleAnimation(AnimationID.HumanIdle)
                .setName("Master Thief")
                .setExamine("TODO")
                .setRemarks(new String[]{"TODO"})
                .setBaseOrientation(CardinalDirection.North));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3216, 3388, 0))
                .setModelIDs(new int[]{
                        14289,
                        25846,
                        2915,
                        25849
                })
                .setModelRecolors(
                        new int[]{
                                22426,
                                22294,
                                6550,
                                8893
                        },
                        new int[]{
                                38065,
                                62637,
                                9152,
                                2983
                        }
                )
                .setIdleAnimation(AnimationID.ChildPlay1)
                .setName("Child")
                .setExamine("TODO")
                .setRemarks(new String[]{"TODO"})
                .setBaseOrientation(CardinalDirection.South));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3220, 3386, 0))
                .setModelIDs(new int[]{
                        14289,
                        25846,
                        2915,
                        25848
                })
                .setIdleAnimation(AnimationID.ChildPlay2)
                .setName("Child")
                .setExamine("TODO")
                .setRemarks(new String[]{"TODO"})
                .setBaseOrientation(CardinalDirection.West));


        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3215, 3419, 0))
                .setModelIDs(new int[]{
                        3010,
                        3006
                })
                .setIdleAnimation(AnimationID.CatSit)
                .setName("Cat")
                // TODO: i broke this
                .setScale((float) 60 / 128, (float) 60 / 128, (float) 60 / 128)
                .setExamine("Are you kitten me right meow?")
                .setRemarks(new String[]{"Meow!"})
                .setBaseOrientation(CardinalDirection.NorthWest)
                .setTranslate(0, 0, -1));

        // TODO: this rat renders weirdly, like its underground?
        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3214, 3420, 0))
                .setModelIDs(new int[]{
                        9610
                })
                .setModelRecolors(
                        new int[]{
                                8741
                        },
                        new int[]{
                                24
                        }
                )
                .setIdleAnimation(AnimationID.RatBanging)
                .setName("Rat")
                .setExamine("Eek!")
                .setBaseOrientation(CardinalDirection.NorthWest)
                .setTranslate(0.3f, -0.8f, 0));


        citizens.add(new WanderingCitizen(this)
                .setBoundingBox(new WorldPoint(3218, 3387, 0), new WorldPoint(3222, 3387, 0))
                .setModelIDs(new int[]{
                        390,
                        456,
                        483,
                        332,
                        353,
                        437,
                        358
                })
                .setModelRecolors(
                        new int[]{
                                8741,
                                25238,
                                6798,
                                43072
                        },
                        new int[]{
                                322,
                                5532,
                                8099,
                                4550
                        }
                )
                .setIdleAnimation(AnimationID.HumanIdle)
                .setName("Mary")
                .setExamine("She has her hands full with those kids."));

        citizens.add(new WanderingCitizen(this)
                .setBoundingBox(new WorldPoint(3214, 3389, 0), new WorldPoint(3236, 3392, 0))
                .setModelIDs(new int[]{
                        2897,
                        2909,
                        2917
                })
                .setModelRecolors(
                        new int[]{
                                22424,
                                11162,
                                6550
                        },
                        new int[]{
                                51088,
                                4,
                                5027
                        }
                )
                .setIdleAnimation(AnimationID.ChildIdle)
                .setMovAnimID(AnimationID.ChildWalk)
                .setName("Child")
                .setExamine("A child."));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3214, 3400, 0))
                .setModelIDs(new int[]{
                        2970,
                        7059,
                        2978,
                        2981,
                        2985
                })
                .setModelRecolors(
                        new int[]{
                                803,
                                61,
                                53,
                                4292,
                                90,
                                2578
                        },
                        new int[]{
                                7322,
                                10646,
                                33,
                                16,
                                16,
                                10539
                        }
                )
                .setIdleAnimation(AnimationID.DwarfLean)
                .setBaseOrientation(CardinalDirection.West)
                .setName("Dwarf")
                .setExamine("A dwarf, possibly waiting for someone?")
                .setTranslate(0.65f, 0, 0));


        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3214, 3387, 0))
                        .setModelIDs(new int[]{1680})
        );


        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3214, 3383, 0))
                        .setModelRecolors(
                                new int[]{
                                        43968
                                },
                                new int[]{
                                        939
                                }
                        )
                        .setModelIDs(new int[]{1680})
        );

        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3217, 3385, 0))
                        .setModelIDs(new int[]{2138})
        );


        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3208, 3384, 0))
                        .setModelRecolors(
                                new int[]{
                                        7587,
                                        7587,
                                        7582,
                                        7706,
                                        8404
                                },
                                new int[]{
                                        7601,
                                        7601,
                                        7721,
                                        7841,
                                        6336
                                }
                        )
                        .setModelIDs(new int[]{14651, 14652})
                        .setBaseOrientation(CardinalDirection.West)
        );

        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3205, 3384, 0))
                        .setModelIDs(new int[]{24840})
                        .setBaseOrientation(CardinalDirection.North)
        );

        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3203, 3385, 0))
                        .setModelIDs(new int[]{13446})
                        .setModelRecolors(
                                new int[]{
                                        6466,
                                        5545
                                },
                                new int[]{
                                        7465,
                                        7580
                                }
                        )
                        .setBaseOrientation(CardinalDirection.East)
        );


        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3210, 3396, 0))
                        .setModelIDs(new int[]{1569})
                        .setBaseOrientation(CardinalDirection.East)
        );

        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3210, 3402, 0))
                        .setModelIDs(new int[]{1569})
                        .setBaseOrientation(CardinalDirection.East)
        );

        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3205, 3384, 0))
                        .setModelIDs(new int[]{24884})
                        .setBaseOrientation(CardinalDirection.East)
        );


        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3202, 3393, 0))
                        .setModelIDs(new int[]{2260, 3818})
                        .setIdleAnimation(AnimationID.FireIdle)
                        .setBaseOrientation(CardinalDirection.East)
        );
        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3201, 3394, 0))
                        .setModelIDs(new int[]{2578})
                        .setBaseOrientation(CardinalDirection.West)
                        .setScale(0.5f, 0.5f, 0.5f)
                        .setTranslate(0, 0, -0.4f)
        );
        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3201, 3394, 0))
                        .setModelIDs(new int[]{2830})
                        .setTranslate(-0.2f, 0, 0.25f)
        );
        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3202, 3394, 0))
                .setModelIDs(new int[]{
                        214,
                        246,
                        292,
                        162,
                        177,
                        18131,
                        18128
                })
                .setIdleAnimation(AnimationID.CurledUp)
                .setBaseOrientation(CardinalDirection.South)
                .setName("Joe the tramp")
                .setExamine("He's had better days."));


        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3221, 3398, 0))
                        .setModelIDs(new int[]{2491})
                        .setTranslate(0, 0.7f, 0.2f)
                        .setModelRecolors(
                                new int[]{
                                        7091,
                                        7116,
                                        54397

                                },
                                new int[]{
                                        520,
                                        520,
                                        63384
                                }
                        )
        );
        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3221, 3397, 0))
                .setModelIDs(new int[]{
                        229,
                        11811,
                        15106,
                        14373,
                        180,
                        12138,
                        181
                })
                .setModelRecolors(
                        new int[]{
                                8741,
                                8860,
                                38036,
                                33030,
                                4550,
                                25238
                        },
                        new int[]{
                                107,
                                90,
                                908,
                                532,
                                918,
                                902
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setBaseOrientation(CardinalDirection.North)
                .setName("Demon butler")
                .setExamine("He's on his day off."));

        scenery.add(
                new Scenery(this)
                        .setWorldLocation(new WorldPoint(3221, 3398, 0))
                        .setModelIDs(new int[]{2468})
                        .setTranslate(0, 0.7f, -0.2f)
        );
        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3221, 3399, 0))
                .setModelIDs(new int[]{
                        6848,
                        9620,
                        12752,
                        10301,
                        12138,
                        181
                })
                .setModelRecolors(
                        new int[]{
                                8741,
                                25238,
                                4626,
                                6798,
                                10330
                        },
                        new int[]{
                                920,
                                33030,
                                33030,
                                8070,
                                94
                        }
                )
                .setIdleAnimation(AnimationID.Sitting)
                .setBaseOrientation(CardinalDirection.South)
                .setName("Butler jarvis")
                .setExamine("He's on his day off."));

        citizens.add(new StationaryCitizen(this)
                .setWorldLocation(new WorldPoint(3224, 3399, 0))
                .setModelIDs(new int[]{
                        25671,
                        25685,
                        25684
                })
                .setIdleAnimation(AnimationID.Sitting)
                .setBaseOrientation(CardinalDirection.East)
                .setName("Guard")
                .setExamine("He's on a break."));


        citizens.add(new ScriptedCitizen(this)
                .setWorldLocation(new WorldPoint(3209, 3425, 0))
                .setModelIDs(new int[]{
                        38135
                })
                .setIdleAnimation(AnimationID.HumanIdle)
                .setBaseOrientation(CardinalDirection.North)
                .setName("ScriptedEmme")
                .setExamine("TODO")
                .setScript(new CitizenScript()
                        .walkTo(3209, 3432)
                        .say("First stop!")
                        .wait(2)
                        .walkTo(3216, 3432)
                        .say("Second stop!")
                        .wait(2)
                        .walkTo(3216, 3425)
                        .say("Third stop!")
                        .wait(2)
                        .walkTo(3209, 3425)
                        .say("I'm back at the start!")
                )
        );


        citizens.add(new ScriptedCitizen(this)
                .setWorldLocation(new WorldPoint(3288, 3371, 0))
                .setModelIDs(new int[]{
                        2971,
                        6019,
                        7059,
                        2979,
                        2990,
                        2980,
                        2985,
                        7053
                })
                .setIdleAnimation(AnimationID.DwarfIdle)
                .setMovAnimID(AnimationID.DwarfWalk)
                .setBaseOrientation(CardinalDirection.South)
                .setName("Dwarf miner")
                .setExamine("I dare not get in his way.")
                .setScript(new CitizenScript()
                        .setAnimation(AnimationID.DwarfMining)
                        .wait(10)
                        .walkTo(3296, 3371)
                        .walkTo(3299, 3373)
                        .walkTo(3298, 3378)
                        .walkTo(3293, 3381)
                        .walkTo(3291, 3388)
                        .walkTo(3291, 3397)
                        .walkTo(3292, 3401)
                        .walkTo(3289, 3409)
                        .walkTo(3286, 3413)
                        .walkTo(3288, 3418)
                        .walkTo(3283, 3428)
                        .walkTo(3253, 3427)
                        .walkTo(3253, 3419)
                        .wait(10)
                        .walkTo(3253, 3423)
                        .walkTo(3253, 3428)
                        .walkTo(3283, 3428)
                        .walkTo(3288, 3418)
                        .walkTo(3289, 3409)
                        .walkTo(3293, 3400)
                        .walkTo(3291, 3393)
                        .walkTo(3293, 3380)
                        .walkTo(3293, 3372)
                        .walkTo(3288, 3371)

                )
        );

        Collections.shuffle(citizens);

        entityCollection.add(citizens);
        entityCollection.add(scenery);
        entitiesAreReady = true;
        citizens.forEach((citizen) -> {
            if (citizen.worldLocation == null) {
                throw new IllegalStateException(citizen.name + " has no initial loc");
            }
        });
    }

    public void loadAnimation(AnimationID animId) {
        clientThread.invoke(() -> {
            Animation anim = client.loadAnimation(animId.getId());
            if (anim == null) {
                throw new IllegalStateException("Tried to load non-existant anim: " + animId);
            }
            animationPoses.add(anim);
        });
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(citizensOverlay);
        despawnAll();
    }

    protected void updateAll() {
        Util.log("Updating all entities");
        getAllEntities().forEach(Entity::update);
    }

    protected void despawnAll() {
        Util.log("Despawning all entities");
        getAllEntities().forEach(Entity::despawn);
    }

    protected Stream<? extends Entity> getAllEntities() {
        return entityCollection.stream().flatMap(List::stream);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            despawnAll();
        }

        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            updateAll();
        }

        panel.update();
    }

    @Schedule(
            period = 5,
            unit = ChronoUnit.SECONDS,
            asynchronous = true
    )
    public void citizenTick() {
        if (!isReady()) {
            return;
        }
        for (Citizen citizen : citizens) {
            if (!citizen.shouldRender() || !citizen.isActive()) {
                continue;
            }
            int random = getRandom(1, 10);
            if (random < 4) {
                if (citizen instanceof WanderingCitizen) {
                    ((WanderingCitizen) citizen).wander();
                }
            }

            if (random == 7 || random == 8 || random == 9) {
                citizen.triggerIdleAnimation();
            }

            if (random == 10) {
                citizen.sayRandomRemark();
            }
        }
    }

    @Subscribe
    public void onClientTick(ClientTick ignored) {
        for (Citizen citizen : citizens) {
            if (citizen.isActive()) {
                citizen.onClientTick();
            }
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened ignored) {
        int firstMenuIndex = 1;

        Point mousePos = client.getMouseCanvasPosition();

        for (Citizen citizen : citizens) {
            if (citizen.isActive()) {
                SimplePolygon clickbox = citizen.getClickbox();
                if (clickbox == null) {
                    continue;
                }
                boolean doesClickBoxContainMousePos = clickbox.contains(mousePos.getX(), mousePos.getY());
                if (doesClickBoxContainMousePos) {
                    client.createMenuEntry(firstMenuIndex)
                          .setOption("Examine")
                          .setTarget("<col=fffe00>" + citizen.name + "</col>")
                          .setType(MenuAction.RUNELITE)
                          .setParam0(0)
                          .setParam1(0)
                          .setDeprioritized(true);
                    break;
                }
            }
        }
    }

    @Subscribe
    private void onMenuOptionClicked(MenuOptionClicked event) {
        if (!event.getMenuOption().equals("Examine")) {
            return;
        }
        for (Citizen citizen : citizens) {
            if (event.getMenuTarget().equals("<col=fffe00>" + citizen.name + "</col>")) {
                event.consume();
                String chatMessage = new ChatMessageBuilder()
                        .append(ChatColorType.NORMAL)
                        .append(citizen.examine)
                        .build();

                chatMessageManager.queue(QueuedMessage.builder().type(ChatMessageType.NPC_EXAMINE)
                                                      .runeLiteFormattedMessage(chatMessage)
                                                      .timestamp((int) (System.currentTimeMillis() / 1000)).build());

                break;
            }
        }
    }

    public int countActiveEntities() {
        return getAllEntities().filter(Entity::isActive).toArray().length;
    }

    public int countInactiveEntities() {
        return getAllEntities().filter(ent -> {
            return !ent.isActive();
        }).toArray().length;
    }

}




