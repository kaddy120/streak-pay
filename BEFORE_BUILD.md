# Before Building - Important Steps

## Required: Generate Gradle Wrapper

The project needs Gradle wrapper files to build. These are missing by design and must be generated.

**Option 1: Using Android Studio (Recommended)**
1. Open the project in Android Studio
2. Studio will automatically generate the wrapper files
3. Sync Gradle when prompted

**Option 2: Using Command Line**
If you have Gradle installed on your system:
```bash
cd /home/kamarindi/workspace/learn/streak-pay
gradle wrapper --gradle-version 8.2 --distribution-type bin
```

This will create:
- `gradlew` (Unix/Mac executable)
- `gradlew.bat` (Windows executable)
- `gradle/wrapper/gradle-wrapper.jar`

## ✅ Launcher Icons - Already Created

Launcher icons have been generated and are ready to use!
- Purple background with white "W" letter
- All densities covered (mdpi through xxxhdpi)
- Adaptive icons for Android 8.0+

See `ICON_FIX_APPLIED.md` for details on customizing the icon if desired.

## Known Issues to Fix Post-Build

### 1. Points Deduction System
Currently, redeeming wish items doesn't deduct points. To implement:

Add to `WishViewModel.kt`:
```kotlin
fun redeemWishItem(wishItem: WishItem) {
    viewModelScope.launch {
        val currentPoints = totalPoints.value
        if (currentPoints >= wishItem.price) {
            // Insert negative session to deduct points
            val deductionSession = Session(
                startTime = LocalDateTime.now(),
                endTime = LocalDateTime.now(),
                durationMinutes = 0,
                pointsEarned = -wishItem.price,
                type = SessionType.SIDE_WORK
            )
            sessionRepository.insertSession(deductionSession)

            // Mark as redeemed
            val updatedWishItem = wishItem.copy(
                isRedeemed = true,
                redeemedDate = LocalDateTime.now()
            )
            wishItemRepository.updateWishItem(updatedWishItem)
        }
    }
}
```

### 2. Settings Screen
Add a settings screen to edit:
- User name
- Daily goals
- Theme preference

### 3. Input Validation
Add validation to:
- Wish item dialog (max price, name length)
- Session minimum duration
- Image size limits

## Build Steps

Once wrapper is generated:

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Or just open in Android Studio and click Run
```

## First Launch

On first app launch:
1. Grant notification permission (Android 13+)
2. Default settings will be initialized:
   - User name: "User"
   - Day job goal: 7.5 hours
   - Side work goal: 4.0 hours

## Testing Checklist

After building, test these core flows:

- [ ] Start timer and let it run for 5 minutes
- [ ] Pause timer, wait, then resume
- [ ] Try to stop before 1 hour on first session (button should be disabled)
- [ ] Stop after 1 hour and verify points earned
- [ ] Add a wish item with image
- [ ] Navigate between all screens
- [ ] Close app and reopen (data should persist)
- [ ] Test streak by working on consecutive days
- [ ] Redeem a wish item
- [ ] Check history stats update correctly

## Troubleshooting

**"Cannot find Gradle wrapper"**
→ Run `gradle wrapper` command above

**"SDK location not found"**
→ Create `local.properties` with: `sdk.dir=/path/to/Android/Sdk`

**"Kotlin version mismatch"**
→ Update to Kotlin 1.9.20 or later

**"Missing launcher icon"**
→ Generate icons using Android Studio Image Asset tool

## Ready to Build?

✅ Gradle wrapper generated
✅ Android Studio installed
✅ SDK 34 downloaded
✅ Device/emulator ready

Then you're good to go! Open the project and hit Run.



Create of distinctive, production-grade frontend interfaces that avoid generic "AI slop" aesthetics. Implement real working code with exceptional attention to aesthetic details and creative choices.

The user provides frontend requirements: a component, page, application, or interface to build. They may include context about the purpose, audience, or technical constraints.

## Design Thinking

Before coding, understand the context and commit to a BOLD aesthetic direction:
- **Purpose**: What problem does this interface solve? Who uses it?
- **Tone**: Pick an extreme: brutally minimal, maximalist chaos, retro-futuristic, organic/natural, luxury/refined, playful/toy-like, editorial/magazine, brutalist/raw, art deco/geometric, soft/pastel, industrial/utilitarian, etc. There are so many flavors to choose from. Use these for inspiration but design one that is true to the aesthetic direction.
- **Constraints**: Technical requirements (framework, performance, accessibility).
- **Differentiation**: What makes this UNFORGETTABLE? What's the one thing someone will remember?

**CRITICAL**: Choose a clear conceptual direction and execute it with precision. Bold maximalism and refined minimalism both work - the key is intentionality, not intensity.

Then implement working code (HTML/CSS/JS, Angular) that is:
- Production-grade and functional
- Visually striking and memorable
- Cohesive with a clear aesthetic point-of-view
- Meticulously refined in every detail

## Frontend Aesthetics Guidelines

Focus on:
- **Typography**: Choose fonts that are beautiful, unique, and interesting. Avoid generic fonts like Arial and Inter; opt instead for distinctive choices that elevate the frontend's aesthetics; unexpected, characterful font choices. Pair a distinctive display font with a refined body font.
- **Color & Theme**: Commit to a cohesive aesthetic. Use CSS variables for consistency. Dominant colors with sharp accents outperform timid, evenly-distributed palettes.
- **Motion**: Use animations for effects and micro-interactions. Prioritize CSS-only solutions for HTML. Use Motion library for React when available. Focus on high-impact moments: one well-orchestrated page load with staggered reveals (animation-delay) creates more delight than scattered micro-interactions. Use scroll-triggering and hover states that surprise.
- **Spatial Composition**: Unexpected layouts. Asymmetry. Overlap. Diagonal flow. Grid-breaking elements. Generous negative space OR controlled density.
- **Backgrounds & Visual Details**: Create atmosphere and depth rather than defaulting to solid colors. Add contextual effects and textures that match the overall aesthetic. Apply creative forms like gradient meshes, noise textures, geometric patterns, layered transparencies, dramatic shadows, decorative borders, custom cursors, and grain overlays.

NEVER use generic AI-generated aesthetics like overused font families (Inter, Roboto, Arial, system fonts), cliched color schemes (particularly purple gradients on white backgrounds), predictable layouts and component patterns, and cookie-cutter design that lacks context-specific character.

Interpret creatively and make unexpected choices that feel genuinely designed for the context. No design should be the same. Vary between light and dark themes, different fonts, different aesthetics. NEVER converge on common choices (Space Grotesk, for example) across generations.

**IMPORTANT**: Match implementation complexity to the aesthetic vision. Maximalist designs need elaborate code with extensive animations and effects. Minimalist or refined designs need restraint, precision, and careful attention to spacing, typography, and subtle details. Elegance comes from executing the vision well.

Remember: Claude is capable of extraordinary creative work. Don't hold back, show what can truly be created when thinking outside the box and committing fully to a distinctive vision.
