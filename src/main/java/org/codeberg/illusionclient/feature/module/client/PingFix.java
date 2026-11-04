package org.codeberg.illusionclient.feature.module.client;

import org.codeberg.illusionclient.feature.module.api.Category;
import org.codeberg.illusionclient.feature.module.api.Description;
import org.codeberg.illusionclient.feature.module.api.Module;
import org.codeberg.illusionclient.feature.module.api.ModuleInfo;

@ModuleInfo(name = "Ping Fix", category = Category.CLIENT)
@Description("Replies to the server instantly while other modules hold packets, so your ping stays low.")
public class PingFix extends Module {
}
