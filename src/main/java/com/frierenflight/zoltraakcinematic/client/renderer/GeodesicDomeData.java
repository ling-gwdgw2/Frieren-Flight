// Copyright (c) 2026 Aling7788565. All Rights Reserved.
package com.frierenflight.zoltraakcinematic.client.renderer;

import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Ultra-high-density Goldberg geodesic dome polyhedron (Frequency 7).
 * Generates 492 dense hexagonal/pentagonal cells and 1,470 unique seamless edges
 * on the unit sphere for anime-accurate forcefield barriers.
 */
public final class GeodesicDomeData {
    public static final List<Cell> CELLS = new ArrayList<>();
    public static final List<Edge> EDGES = new ArrayList<>();

    public record Cell(Vec3 center, Vec3[] vertices) {}
    public record Edge(Vec3 p1, Vec3 p2, Vec3 normal) {}

    private record Key(long x, long y, long z) {}
    private record Corner(double ang, int tid) {}

    static {
        generateDome(7);
    }

    private static void generateDome(int f) {
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        double normBase = Math.sqrt(1.0 + phi * phi);

        Vec3[] icoVerts = new Vec3[]{
                new Vec3(-1.0 / normBase,  phi / normBase, 0.0),
                new Vec3( 1.0 / normBase,  phi / normBase, 0.0),
                new Vec3(-1.0 / normBase, -phi / normBase, 0.0),
                new Vec3( 1.0 / normBase, -phi / normBase, 0.0),
                new Vec3(0.0, -1.0 / normBase,  phi / normBase),
                new Vec3(0.0,  1.0 / normBase,  phi / normBase),
                new Vec3(0.0, -1.0 / normBase, -phi / normBase),
                new Vec3(0.0,  1.0 / normBase, -phi / normBase),
                new Vec3( phi / normBase, 0.0, -1.0 / normBase),
                new Vec3( phi / normBase, 0.0,  1.0 / normBase),
                new Vec3(-phi / normBase, 0.0, -1.0 / normBase),
                new Vec3(-phi / normBase, 0.0,  1.0 / normBase)
        };

        int[][] icoFaces = new int[][]{
                {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
                {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
                {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
                {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}
        };

        List<Vec3> uniqueVerts = new ArrayList<>();
        Map<Key, Integer> vertMap = new HashMap<>();
        List<int[]> triangles = new ArrayList<>();

        for (int[] face : icoFaces) {
            Vec3 a = icoVerts[face[0]];
            Vec3 b = icoVerts[face[1]];
            Vec3 c = icoVerts[face[2]];
            int[][] grid = new int[f + 1][f + 1];

            for (int i = 0; i <= f; i++) {
                for (int j = 0; j <= f - i; j++) {
                    int k = f - i - j;
                    double vx = (i * a.x + j * b.x + k * c.x) / f;
                    double vy = (i * a.y + j * b.y + k * c.y) / f;
                    double vz = (i * a.z + j * b.z + k * c.z) / f;
                    Vec3 vNorm = new Vec3(vx, vy, vz).normalize();
                    Key key = new Key(Math.round(vNorm.x * 100000.0), Math.round(vNorm.y * 100000.0), Math.round(vNorm.z * 100000.0));
                    Integer id = vertMap.get(key);
                    if (id == null) {
                        id = uniqueVerts.size();
                        vertMap.put(key, id);
                        uniqueVerts.add(vNorm);
                    }
                    grid[i][j] = id;
                }
            }

            for (int i = 0; i < f; i++) {
                for (int j = 0; j < f - i; j++) {
                    triangles.add(new int[]{grid[i][j], grid[i + 1][j], grid[i][j + 1]});
                    if (i + j < f - 1) {
                        triangles.add(new int[]{grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1]});
                    }
                }
            }
        }

        Map<Integer, List<Integer>> vertTris = new HashMap<>();
        for (int tid = 0; tid < triangles.size(); tid++) {
            for (int vid : triangles.get(tid)) {
                vertTris.computeIfAbsent(vid, k -> new ArrayList<>()).add(tid);
            }
        }

        List<Vec3> triCentroids = new ArrayList<>(triangles.size());
        for (int[] tri : triangles) {
            Vec3 v0 = uniqueVerts.get(tri[0]);
            Vec3 v1 = uniqueVerts.get(tri[1]);
            Vec3 v2 = uniqueVerts.get(tri[2]);
            triCentroids.add(new Vec3((v0.x + v1.x + v2.x) / 3.0, (v0.y + v1.y + v2.y) / 3.0, (v0.z + v1.z + v2.z) / 3.0).normalize());
        }

        Map<Long, Edge> edgeMap = new LinkedHashMap<>();

        for (int vid = 0; vid < uniqueVerts.size(); vid++) {
            Vec3 v = uniqueVerts.get(vid);
            List<Integer> tids = vertTris.get(vid);

            Vec3 u = (Math.abs(v.y) < 0.99 ? v.cross(new Vec3(0.0, 1.0, 0.0)) : v.cross(new Vec3(1.0, 0.0, 0.0))).normalize();
            Vec3 w = v.cross(u);

            List<Corner> corners = new ArrayList<>();
            for (int tid : tids) {
                Vec3 c = triCentroids.get(tid);
                double projX = c.x - v.x * v.dot(c);
                double projY = c.y - v.y * v.dot(c);
                double projZ = c.z - v.z * v.dot(c);
                Vec3 proj = new Vec3(projX, projY, projZ);
                double ang = Math.atan2(proj.dot(w), proj.dot(u));
                corners.add(new Corner(ang, tid));
            }
            corners.sort(Comparator.comparingDouble(Corner::ang));

            Vec3[] verts = new Vec3[corners.size()];
            for (int i = 0; i < corners.size(); i++) {
                verts[i] = triCentroids.get(corners.get(i).tid);
            }
            CELLS.add(new Cell(v, verts));

            for (int i = 0; i < corners.size(); i++) {
                int t1 = corners.get(i).tid;
                int t2 = corners.get((i + 1) % corners.size()).tid;
                long pairKey = ((long) Math.min(t1, t2) << 32) | (long) Math.max(t1, t2);
                if (!edgeMap.containsKey(pairKey)) {
                    Vec3 c1 = triCentroids.get(t1);
                    Vec3 c2 = triCentroids.get(t2);
                    Vec3 norm = new Vec3((c1.x + c2.x) * 0.5, (c1.y + c2.y) * 0.5, (c1.z + c2.z) * 0.5).normalize();
                    edgeMap.put(pairKey, new Edge(c1, c2, norm));
                }
            }
        }
        EDGES.addAll(edgeMap.values());
    }
}
