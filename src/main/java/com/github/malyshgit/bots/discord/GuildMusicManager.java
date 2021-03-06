package com.github.malyshgit.bots.discord;

import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * Holder for both the player and a track scheduler for one guild.
 */
public class GuildMusicManager {
    /**
     * Audio player for the guild.
     */
    public final AudioPlayer player;
    /**
     * Track scheduler for the player.
     */
    public final TrackScheduler scheduler;

    public final MAudioProvider provider;

    /**
     * Creates a player and a track scheduler.
     * @param manager Audio player manager to use for creating the player.
     */
    public GuildMusicManager(AudioPlayerManager manager) {
        manager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.LOW);
        manager.getConfiguration().setOpusEncodingQuality(1);
        player = manager.createPlayer();
        player.setFrameBufferDuration(500);
        scheduler = new TrackScheduler(player);
        player.addListener(scheduler);
        provider = new MAudioProvider(player);
    }
}