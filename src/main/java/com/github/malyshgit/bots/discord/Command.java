package com.github.malyshgit.bots.discord;

import discord4j.core.event.domain.message.MessageCreateEvent;

interface Command {
    // Since we are expecting to do reactive things in this method, like
    // send a message, then this method will also return a reactive type.
    void execute(MessageCreateEvent event);
}