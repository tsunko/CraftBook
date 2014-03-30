package com.me4502.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockState;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.BukkitUtil;
import com.sk89q.craftbook.circuits.ic.ChipState;
import com.sk89q.craftbook.circuits.ic.ConfigurableIC;
import com.sk89q.craftbook.circuits.ic.IC;
import com.sk89q.craftbook.circuits.ic.ICConfiguration;
import com.sk89q.craftbook.circuits.ic.ICFamily;
import com.sk89q.craftbook.circuits.ic.ICManager;
import com.sk89q.craftbook.circuits.ic.RegisteredICFactory;
import com.sk89q.craftbook.circuits.ic.families.FamilyAISO;
import com.sk89q.craftbook.util.developer.ExternalUtilityBase;
import com.sk89q.util.yaml.YAMLFormat;
import com.sk89q.util.yaml.YAMLProcessor;

public class GenerateWikiICPages extends ExternalUtilityBase {

    public GenerateWikiICPages (String[] args) {
        super(args);
    }

    @Override
    public void generate(String[] args) {
        try {

            boolean upload = false;
            List<String> toUpload = new ArrayList<String>();

            for(String arg : args) {
                if(arg.equalsIgnoreCase("upload"))
                    upload = true;
                else if(upload)
                    toUpload.add(arg.toUpperCase());
            }

            File file = new File(getGenerationFolder(), "IC-Pages/");
            if(!file.exists())
                file.mkdir();

            BlockState oldState = Bukkit.getWorlds().get(0).getBlockAt(0, 255, 0).getState();
            Bukkit.getWorlds().get(0).getBlockAt(0, 255, 0).setType(Material.WALL_SIGN);

            CraftBookPlugin.inst().createDefaultConfiguration(new File(getGenerationFolder(), "ic-config.yml"), "ic-config.yml");
            ICConfiguration icConfiguration = new ICConfiguration(new YAMLProcessor(new File(getGenerationFolder(), "ic-config.yml"), true, YAMLFormat.EXTENDED), CraftBookPlugin.logger());

            icConfiguration.load();

            int missingComments = 0;

            Set<String> missingDocuments = new HashSet<String>();

            for(RegisteredICFactory ric : ICManager.inst().getICList()) {

                PrintWriter writer = new PrintWriter(new File(file, ric.getId() + ".txt"), "UTF-8");

                IC ic = ric.getFactory().create(null);

                for(ICFamily family : ric.getFamilies()) {
                    if(family instanceof FamilyAISO) continue;
                    writer.println("{{" + family.getName() + "|id=" + ric.getId() + "|name=" + ic.getTitle() + "}}");
                }

                if(ric.getFactory().getLongDescription() == null || ric.getFactory().getLongDescription().length == 0 || ric.getFactory().getLongDescription()[0].equals("Missing Description")) {
                    CraftBookPlugin.logger().info("Missing Long Description for: " + ric.getId());
                    missingDocuments.add(ric.getId());
                }

                for(String line : ric.getFactory().getLongDescription())
                    writer.println(line);

                writer.println();
                writer.println("== Sign parameters ==");
                writer.println("# " + ic.getSignTitle());
                writer.println("# [" + ric.getId() + "]");
                for(String line : ric.getFactory().getLineHelp())
                    writer.println("# " + (line == null ? "Blank" : line));

                writer.println();
                writer.println("== Pins ==");

                writer.println();
                writer.println("=== Input ===");
                int pins = 0;

                ChipState state = ric.getFamilies()[0].detect(BukkitUtil.toWorldVector(Bukkit.getWorlds().get(0).getBlockAt(0, 255, 0)), BukkitUtil.toChangedSign(Bukkit.getWorlds().get(0).getBlockAt(0, 255, 0)));

                for(String pin : ric.getFactory().getPinDescription(state)) {

                    if(pins == state.getInputCount()) {
                        writer.println();
                        writer.println("=== Output ===");
                    }

                    writer.println("# " + (pin == null ? "Nothing" : pin));

                    if(pin == null) {
                        CraftBookPlugin.logger().info("Missing pin: " + pins + " for IC: " + ric.getId());
                        missingDocuments.add(ric.getId());
                    }

                    pins++;
                }

                if(ric.getFactory() instanceof ConfigurableIC) {

                    writer.println();
                    writer.println("== Configuration ==");
                    writer.println();
                    writer.println("{| class=\"wiki-table sortable\"");
                    writer.println("|-");
                    writer.println("! Configuration Node and Path");
                    writer.println("! Default Value");
                    writer.println("! Effect");

                    String path = "ics." + ric.getId();

                    for(String key : icConfiguration.config.getKeys(path)) {
                        if(icConfiguration.config.getProperty(path + "." + key) != null && !(icConfiguration.config.getProperty(path + "." + key) instanceof Map)) {
                            writer.println("|-");
                            writer.println("| " + path + "." + key);
                            writer.println("| " + String.valueOf(icConfiguration.config.getProperty(path + "." + key)));
                            String comment = icConfiguration.config.getComment(path + "." + key);
                            if(comment == null) {
                                System.out.println("[WARNING] Key " + path + "." + key + " is missing a comment!");
                                missingComments++;
                                missingDocuments.add(ric.getId());
                                comment = "";
                            }
                            if(!comment.trim().isEmpty()) comment = comment.trim().substring(2);
                            writer.println("| " + comment);
                        }
                    }

                    writer.println("|}");
                }

                writer.println();
                writer.print("[[Category:IC]]");
                for(ICFamily family : ric.getFamilies())
                    writer.print("[[Category:" + family.getName() + "]]");
                writer.close();
            }

            System.out.println(missingComments + " Comments Are Missing");

            oldState.update(true);

            if(upload) {
                for(RegisteredICFactory ric : ICManager.inst().getICList()) {
                    if(toUpload.contains("all") || toUpload.contains(ric.getId())) {

                        if(missingDocuments.contains(ric.getId())) continue; //Ignore this, bad docs.
                        //TODO wiki auto upload.
                    }
                }
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}