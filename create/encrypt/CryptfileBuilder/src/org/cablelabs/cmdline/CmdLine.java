
package org.cablelabs.cmdline;

/**
 * Command line arg parsing helpers
 *
 */
public class CmdLine {
    
    private Usage usage;
    
    public CmdLine(Usage usage) {
        this.usage = usage;
    }
    
    private void invalidOption(String option) {
        errorExit("Invalid argument specification for " + option);
    }
    
    // Check for the presence of an option argument and validate that there are enough sub-options to
    // satisfy the option's requirements
    public String[] checkOption(String optToCheck, String[] args, int current,
            int minSubopts, int maxSubopts) {
        if (!args[current].equals(optToCheck))
            return null;
        
        // No sub-options required
        if (minSubopts == 0 && maxSubopts == 0)
            return new String[0];
        
        // Validate that the sub-options are present
        if (args.length < current + 1)
            invalidOption(optToCheck);
        
        // Check that the sub-options present satifsy the min/max requirements
        String[] subopts = args[current+1].split(",");
        if (subopts.length < minSubopts || subopts.length > maxSubopts)
            invalidOption(optToCheck);
        
        return subopts;
    }
    public String[] checkOption(String optToCheck, String[] args, int current, int subopts) {
        return checkOption(optToCheck, args, current, subopts, subopts);
    }
    
    public void errorExit(String errorString) {
        usage.usage();
        System.err.println(errorString);
        System.exit(1);
    }

}
