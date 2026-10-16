package dev.professional.blog.carwash;

import java.math.BigDecimal;

public class MutableCar implements Washable {
    private String plate;
    private Owner owner;
    private int dirtLevel;
    private WashStatus status;

    public MutableCar(String plate, Owner owner, int dirtLevel) {
        this(plate, owner, dirtLevel, WashStatus.DIRTY);
    }

    public MutableCar(String plate, Owner owner, int dirtLevel, WashStatus status) {
        this.plate = plate;
        this.owner = owner;
        this.dirtLevel = dirtLevel;
        this.status = status;
    }

    public String plate() { return plate; }
    public Owner owner() { return owner; }
    public int dirtLevel() { return dirtLevel; }
    public WashStatus status() { return status; }

    public BigDecimal quoteWash() {
        return BigDecimal.valueOf(8).add(BigDecimal.valueOf(dirtLevel * 2L));
    }

    public void inspect() {
        if (plate == null || dirtLevel == 0) {
            throw new IllegalArgumentException("Nothing to wash");
        }
        this.status = WashStatus.INSPECTED;
    }

    public void scrub() {
        if (status != WashStatus.INSPECTED) {
            throw new IllegalStateException("Car must be inspected first");
        }
        this.dirtLevel = Math.max(0, dirtLevel - 4);
        this.status = WashStatus.SCRUBBED;
    }

    public void wax() {
        if (status != WashStatus.SCRUBBED) {
            throw new IllegalStateException("Car must be scrubbed first");
        }
        this.dirtLevel = Math.max(0, dirtLevel - 2);
        this.status = WashStatus.WAXED;
    }

    public void dry() {
        if (status != WashStatus.WAXED) {
            throw new IllegalStateException("Car must be waxed first");
        }
        this.status = WashStatus.DRIED;
    }

    public void runWashCycle() {
        inspect();
        scrub();
        wax();
        dry();
    }
}
