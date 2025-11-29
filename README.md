# Hytale Example Plugin

An example project that can build and run plugins for the game Hytale!

> **⚠️ Warning: Early Access**    
> The game Hytale is in early access, and so is this project! Features may be
> incomplete, unstable, or change frequently. Please be patient and understanding as development
> continues.

## Introduction

This project contains a Gradle project that can be imported into IDEA and used
as the foundation for custom Hytale plugins. The template will add the Hytale
server to your classpath and create a run configuration that can be used to
run your plugin on the server. It can also be used to build a sharable JAR file
that contains your plugin.

## Requirements

Please ensure all the requirements are met before getting started.

1. Windows 11
2. Download Hytale using the official launcher.
3. Have Intellij IDEA installed. Community edition is fine.
4. Download Java 25 and add it as an SDK in IDEA.

## Configuring Template

It is important to configure the project before using it as a template. Doing
this before importing the project will help avoid running into caching issues
later on.

### 1: Project Name

Set the name of the project in `settings.gradle`. This variable is referenced
throughout the project and controls things like the name of your plugin when
viewed in game. We recommend capitalizing your project name and avoiding
whitespace and most special characters.

### 2: Gradle Properties

These properties can be found in `gradle.properties`.

- `group` - The Java group of your project. This is usually written in reverse
  domain style to avoid conflicts. For example if you own `example.org` and your
  project is called `plugin` you may use `org.example.plugin`.
- `java_version` - The version of Java your plugin will be compiled with.
- `plugin_group` - The group of your plugin in Hytale. Currently, this should
  be your name or your organization, start with a capital letter, and be one
  word. For example `ExampleModder` or `ExampleStudios`.
- `version` - The version of your plugin. You should change this manually
  before each release, or incorporate the build number if you are using CI.
- `description` - A description of your plugin that is displayed in game.
- `author` - An author credit that is displayed in game.
- `website` - A website or homepage for your project.
- `sever_version` - The version of the server your plugin can load on. A `*`
  means any version will work.
- `entry_point` - The Java class file the Hytale plugin manager should load.
  This is where your plugin will start loading.

## Importing into IDEA

When opening the project in IDEA it should automatically create the
`HytaleServer` run configuration and a `./run/server/` folder. When you run the
game it will generate all the relevant files in there. It will also load the
default assets and packs from the games actual install location.

**If you do not see the `HytaleServer` run configuration, you may need to open
the dropdown or click `Edit Configurations...` once to unhide it.**

## Connecting to Server

Once the server is running in IDEA you should be able to connect to 
`Local Server` using your standard Hytale client. If the server does not show
up automatically, add the IP as `127.0.0.1` manually.

## Loading Packs, Assets, and Textures

Hytale does not currently load packs, assets, or textures from plugins. Their
team is aware of this limitation and are looking into it. For now, please check
out the plugin_with_pack branch for a simple workaround.

