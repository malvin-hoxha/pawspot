import { HeroSection } from '../components/home/HeroSection'
import { LostFoundSection } from '../components/home/LostFoundSection'
import { LatestPostsSection } from '../components/home/LatestPostsSection'

export function HomePage() {
  return (
    <>
      <HeroSection />
      <LostFoundSection />
      <LatestPostsSection />
    </>
  )
  
}