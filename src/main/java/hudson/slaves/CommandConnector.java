/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.slaves;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import java.io.IOException;

import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.command_launcher.Messages;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.SystemCommandLanguage;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Executes a program on the controller and expect that script to connect.
 *
 * @author Kohsuke Kawaguchi
 */
public class CommandConnector extends ComputerConnector {
    public final String command;

    @DataBoundConstructor
    public CommandConnector(String command) {
        this.command = command;
        // TODO add withKey if we can determine the Cloud.name being configured
        ScriptApproval.get().configuring(command, SystemCommandLanguage.get(), ApprovalContext.create().withCurrentUser(), Stapler.getCurrentRequest2() == null);
    }

    private Object readResolve() {
        ScriptApproval.get().configuring(command, SystemCommandLanguage.get(), ApprovalContext.create(), true);
        return this;
    }

    @Override
    public CommandLauncher launch(String host, TaskListener listener) throws IOException, InterruptedException {
        // no need to call ScriptApproval.using here; CommandLauncher.launch will do that
        return new CommandLauncher(new EnvVars("SLAVE", host, "AGENT", host), command);
    }

    @Extension @Symbol("command")
    public static class DescriptorImpl extends ComputerConnectorDescriptor {
        @Override
        public ComputerConnector newInstance(@Nullable StaplerRequest2 req, @NonNull JSONObject formData) throws FormException {
            CommandConnector instance = (CommandConnector) super.newInstance(req, formData);
            if (formData.get("oldCommand") != null) {
                String oldCommand = formData.getString("oldCommand");
                boolean approveIfAdmin = !StringUtils.equals(oldCommand, instance.command);
                if (approveIfAdmin) {
                    ScriptApproval.get().configuring(instance.command, SystemCommandLanguage.get(),
                            ApprovalContext.create().withCurrentUser(), true);
                }
            }
            return instance;
        }

        @Override
        public String getDisplayName() {
            return Messages.CommandLauncher_displayName();
        }

        public FormValidation doCheckCommand(@QueryParameter String value, @QueryParameter String oldCommand) {
            if (Util.fixEmptyAndTrim(value) == null) {
                return FormValidation.error(Messages.CommandLauncher_NoLaunchCommand());
            } else {
                return ScriptApproval.get().checking(value, SystemCommandLanguage.get(), !StringUtils.equals(value, oldCommand));
            }
        }

    }
}
