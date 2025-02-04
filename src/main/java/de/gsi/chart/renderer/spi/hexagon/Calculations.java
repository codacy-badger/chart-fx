package de.gsi.chart.renderer.spi.hexagon;

import java.util.ArrayList;
import java.util.Collections;

class Calculations {
    static ArrayList<Hexagon> getPathBetween(final Hexagon start, final Hexagon destination,
            final IPathInfoSupplier pathInfoSupplier) throws NoPathFoundException {
        final ArrayList<Hexagon> closedSet = new ArrayList<>(); // The set of nodes already evaluated
        final ArrayList<Hexagon> openSet = new ArrayList<>(); // The set of tentative nodes to be evaluated, initially
                                                              // containing the start node
        openSet.add(start);
        start.aStarGscore = 0;
        start.aStarFscore = start.aStarGscore + GridPosition.getDistance(start.position, destination.position);

        Hexagon currentHexagon;
        int tentative_g_score;
        while (!openSet.isEmpty()) {
            currentHexagon = Calculations.findHexagonWithLowestFscore(openSet);
            if (currentHexagon.position.equals(destination.position)) {
                return Calculations.reconstruct_path(start, destination);
            }
            openSet.remove(currentHexagon);
            closedSet.add(currentHexagon);

            for (final Hexagon neighbour : currentHexagon.getNeighbours()) {
                if ((!pathInfoSupplier.isBlockingPath(neighbour) || destination.equals(neighbour)) && !closedSet.contains(neighbour)) {
				    tentative_g_score = currentHexagon.aStarGscore
				            + pathInfoSupplier.getMovementCost(currentHexagon, neighbour);

				    if (!openSet.contains(neighbour) || tentative_g_score < neighbour.aStarGscore) {
				        neighbour.aStarCameFrom = currentHexagon;
				        neighbour.aStarGscore = tentative_g_score;
				        neighbour.aStarFscore = neighbour.aStarGscore
				                + GridPosition.getDistance(neighbour.position, destination.position);

				        /*
				         * TODO: Vill få den att generera path som är mer som getLine() så att de inte rör sig
				         * kantigt på kartan. Nedanstående funkar sådär:
				         * neighbour.aStarFscore = neighbour.aStarGscore +
				         * neighbour.getGraphicsDistanceTo(destination);
				         *
				         * Ett sätt kunde vara att undersöka om man kan identifiera hex där path går runt ett hörn
				         * (har de unika g-värden?), dvs en ruta som definitivt ska besökas och sedan mäta det
				         * grafiska avståndet till dem som f-värde.
				         */
				        if (!openSet.contains(neighbour)) {
				            openSet.add(neighbour);
				        }
				    }
				}
            }
        }
        throw new NoPathFoundException("Can't find any path to the goal Hexagon");
    }

    private static Hexagon findHexagonWithLowestFscore(final ArrayList<Hexagon> openSet) {
        Hexagon hexagonWithLowestFscore = openSet.get(0); // Just pick anyone and then see if we can find any better
        int lowestFscore = hexagonWithLowestFscore.aStarFscore;
        for (final Hexagon h : openSet) {
            if (h.aStarFscore < lowestFscore) {
                hexagonWithLowestFscore = h;
                lowestFscore = h.aStarFscore;
            }
        }
        return hexagonWithLowestFscore;
    }

    private static ArrayList<Hexagon> reconstruct_path(final Hexagon start, final Hexagon goal) {
        final ArrayList<Hexagon> path = new ArrayList<>();
        Hexagon currentHexagon = goal;
        while (currentHexagon != start) {
            path.add(currentHexagon);
            currentHexagon = currentHexagon.aStarCameFrom;
        }
        Collections.reverse(path);
        return path;
    }

    static ArrayList<Hexagon> getLine(final GridPosition origin, final GridPosition destination, final HexagonMap map) {
        Hexagon h;
        final ArrayList<Hexagon> result = new ArrayList<>();
        final ArrayList<GridPosition> positions = origin.line(destination);

        for (final GridPosition position : positions) {
            h = map.getHexagon(position);
            if (h != null) {
                result.add(h);
            }
        }
        return result;
    }

    static ArrayList<Hexagon> getVisibleHexes(final Hexagon origin, final int visibleRange, final HexagonMap map) {
        final ArrayList<GridPosition> ringMembers = origin.position.getPositionsOnCircleEdge(visibleRange);
        final ArrayList<Hexagon> result = new ArrayList<>();
        ArrayList<Hexagon> line;
        for (final GridPosition ringMemberPosition : ringMembers) {
            line = Calculations.getLine(origin.position, ringMemberPosition, map);
            for (final Hexagon hexagonInLine : line) {
                result.add(hexagonInLine);
                if (hexagonInLine.isVisualObstacle()) {
                    break;
                }
            }
        }
        return result;
    }

    static ArrayList<Hexagon> getHexagonsOnRingEdge(final Hexagon center, final int radius, final HexagonMap map) {
        final ArrayList<Hexagon> result = new ArrayList<>();
        for (final GridPosition position : center.position.getPositionsOnCircleEdge(radius)) {
            final Hexagon hexagon = map.getHexagon(position);
            if (hexagon != null) {
                result.add(hexagon);
            }
        }
        return result;
    }

    static ArrayList<Hexagon> getHexagonsInRingArea(final Hexagon center, final int radius, final HexagonMap map) {
        final ArrayList<Hexagon> result = new ArrayList<>();
        for (final GridPosition position : center.position.getPositionsInCircleArea(radius)) {
            final Hexagon hexagon = map.getHexagon(position);
            if (hexagon != null) {
                result.add(hexagon);
            }
        }
        return result;
    }
}
