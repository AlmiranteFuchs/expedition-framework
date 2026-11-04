"""Regression checks for coplanar joins, transparent chambers and rotated grain."""
import unittest
from machine_surface_mesh import exposed,cropped_uv
from verify_machine_models import overlaps


def cube(a,b,texture):return {'from':a,'to':b,'faces':{f:{'texture':texture,'uv':[0,0,16,16]} for f in ['up','down','north','south','east','west']}}


class MachineSurfaces(unittest.TestCase):
    def test_overlapping_join_keeps_solids_and_one_exposed_skin(self):
        original=[cube([0,0,0],[10,3,10],'#bone'),cube([0,0,0],[3,3,4],'#iron')]
        result=exposed(original)
        self.assertEqual([],overlaps({'elements':result}))
        bodies=[e for e in result if e.get('expeditions_collision_only')]
        self.assertEqual([(e['from'],e['to']) for e in original],[(e['from'],e['to']) for e in bodies])
        self.assertTrue(all(e['faces'] for e in result),'Minecraft rejects elements without faces')

    def test_glass_does_not_hide_the_resin_inside_it(self):
        original=[cube([0,0,0],[16,16,16],'#glass'),cube([4,4,4],[12,12,12],'#resin')]
        result=exposed(original)
        resin=[e for e in result if e.get('expeditions_render_only') and next(iter(e['faces'].values()))['texture']=='#resin']
        self.assertEqual(6,len(resin))

    def test_cropped_rotated_uvs_follow_minecraft_corner_order(self):
        original=cube([0,0,0],[16,16,16],'#bone');rect=[4,2,12,10]
        for rotation,expected in [(0,[4,6,12,14]),(90,[6,4,14,12]),(180,[4,2,12,10]),(270,[2,4,10,12])]:
            self.assertEqual(expected,cropped_uv({'uv':[0,0,16,16],'rotation':rotation},original,rect,'south'))

    def test_empty_core_is_still_a_single_removable_placeholder(self):
        original=[cube([6,9,6],[10,13,10],'#empty')]
        self.assertEqual(original,exposed(original))


if __name__=='__main__':unittest.main()
