# Smart Laundry Facility Simulation (CT074-3-2) - NPI000329

## Run in Apache NetBeans
File > Open Project, pick this `SmartLaundrySimulation` folder (the one with `pom.xml`), press F6.
Main class: `SmartLaundrySimulation` (all sources are in `src/main/java`, no packages). JDK 11 or newer.

## Run without NetBeans
    cd src/main/java
    javac *.java
    java SmartLaundrySimulation              (GUI)
    java ConsoleRunner normal 4              (headless run + automatic safety self-check)

`ConsoleRunner` arguments: scenario `normal` | `congested` | `incidents`, speed `1` | `2` | `4`.
Exit code 0 = every check passed (50/50 served, no machine double-used, peaks within machine counts).

## Scenarios (toggle in the GUI header)
- **Normal run** - basic + additional requirements; random 5 % washer faults (mid-cycle) and 5 % kiosk faults.
- **Bonus: Congested** - both kiosks broken all day; owner called at 30 waiting; waiting area holds 31.
- **Extra: Incidents** - scripted incidents (single machine, both kiosks, two washers, single kiosk) with owner repairs.

Speed 1x / 2x / 4x can be changed at any time. The scenario can be changed after Reset or when a run has finished.
Each finished run writes `simulation_results.txt` (statistics incl. average waits, utilisation, throughput).
