package com.github.malyshgit.bots.discord;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.core.DiscordClient;
import discord4j.core.event.EventDispatcher;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args){
        DiscordClient client = DiscordClient.create(args.length > 0 ? args[0] : Config.TOKEN);
        new Main().registerListeners(client.getEventDispatcher());
        client.login().block();
    }

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private Main() {
        this.musicManagers = new ConcurrentHashMap<>();

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);
    }

    private void registerListeners(EventDispatcher eventDispatcher) {
        eventDispatcher.on(MessageCreateEvent.class).subscribe(this::onMessageReceived);
    }

    private synchronized GuildMusicManager getGuildAudioPlayer(Guild guild) {
        long guildId = guild.getId().asLong();
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        return musicManager;
    }

    private void onMessageReceived(MessageCreateEvent event) {
        Message message = event.getMessage();

        message.getContent().ifPresent(it -> {
            MessageChannel channel = message.getChannel().block();

            if (channel instanceof TextChannel) {
                String[] command = it.split(" ", 2);

                if ("!play".equals(command[0]) && command.length == 2) {
                    loadAndPlay((TextChannel) channel, command[1]);
                } else if ("!pause".equals(command[0])) {
                    pause((TextChannel) channel);
                } else if ("!clear".equals(command[0])) {
                    clearQueue((TextChannel) channel);
                } else if ("!skip".equals(command[0])) {
                    skipTrack((TextChannel) channel);
                }
            }
        });
    }

    private void loadAndPlay(TextChannel channel, final String trackUrl) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild().block());

        playerManager.loadItemOrdered(musicManager, trackUrl, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                sendMessageToChannel(channel, "Добавлено в очередь " + track.getInfo().title);

                play(channel.getGuild().block(), musicManager, track, channel.getName());
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                playlist.getTracks().forEach(t -> {
                    play(channel.getGuild().block(), musicManager, t, channel.getName());
                });
                sendMessageToChannel(channel, "Добавлено в очередь " + playlist.getTracks().size() + " треков из плейлиста " + playlist.getName() + "");

                /*AudioTrack firstTrack = playlist.getSelectedTrack();

                if (firstTrack == null) {
                    firstTrack = playlist.getTracks().get(0);
                }

                sendMessageToChannel(channel, "Добавлено в очередь " + firstTrack.getInfo().title + " (Первый трек в плейлисте " + playlist.getName() + ")");

                play(channel.getGuild().block(), musicManager, firstTrack, channel.getName());*/
            }

            @Override
            public void noMatches() {
                sendMessageToChannel(channel, "Ничего не найдено по ссылке: " + trackUrl);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                sendMessageToChannel(channel, "Не удалось воспроизвести: " + exception.getMessage());
            }
        });
    }

    private void play(Guild guild, GuildMusicManager musicManager, AudioTrack track, String channelName) {
        GuildMusicManager manager = getGuildAudioPlayer(guild);
        attachToVoiceChannel(guild, manager.provider, channelName);
        musicManager.scheduler.queue(track);
    }

    private void clearQueue(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild().block());
        musicManager.scheduler.clear();

        sendMessageToChannel(channel, "Очередь очищена.");
    }

    private void pause(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild().block());
        var isPaused = musicManager.scheduler.pause();

        sendMessageToChannel(channel, isPaused ? "Поставлено на паузу." : "Снято с паузы.");
    }

    private void queue(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild().block());
        var trackList = musicManager.scheduler.trackList();

        sendMessageToChannel(channel, trackList);
    }

    private void skipTrack(TextChannel channel) {
        GuildMusicManager musicManager = getGuildAudioPlayer(channel.getGuild().block());
        musicManager.scheduler.nextTrack();

        sendMessageToChannel(channel, "Следующий трек.");
    }

    private void sendMessageToChannel(TextChannel channel, String message) {
        try {
            channel.createMessage(message).block();
        } catch (Exception e) {
            log.warn("Failed to send message {} to {}", message, channel.getName(), e);
        }
    }

    private static void attachToVoiceChannel(Guild guild, MAudioProvider provider, String channelName) {
        VoiceChannel voiceChannel = guild.getChannels().ofType(VoiceChannel.class).filter(c -> c.getName().equals(channelName)).blockFirst();
        boolean inVoiceChannel = guild.getVoiceStates() // Check if any VoiceState for this guild relates to bot
                .any(voiceState -> guild.getClient().getSelfId().map(voiceState.getUserId()::equals).orElse(false))
                .block();

        if (!inVoiceChannel) {
            voiceChannel.join(spec -> spec.setProvider(provider)).block();
        }
    }
}