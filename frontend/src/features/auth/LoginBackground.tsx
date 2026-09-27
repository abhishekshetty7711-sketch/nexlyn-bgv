import loginJpg800 from '@/assets/login/login-800.jpg'
import loginJpg1200 from '@/assets/login/login-1200.jpg'
import loginJpg1800 from '@/assets/login/login-1800.jpg'
import loginWebp800 from '@/assets/login/login-800.webp'
import loginWebp1200 from '@/assets/login/login-1200.webp'
import loginWebp1800 from '@/assets/login/login-1800.webp'

// The image is a tall photograph: full-bleed on a phone, the right-hand 40% (tablet) or 55% (desktop) of the
// screen otherwise. Three heights (800/1200/1800), WebP with a JPEG fallback, so a phone never fetches the
// 1800 px file. Widths are the pixels sharp actually produced (the source is 2828x4180), not rounded guesses.
const SIZES = '(max-width: 767px) 100vw, (max-width: 1023px) 40vw, 55vw'
const WEBP_SRCSET = `${loginWebp800} 541w, ${loginWebp1200} 812w, ${loginWebp1800} 1218w`
const JPG_SRCSET = `${loginJpg800} 541w, ${loginJpg1200} 812w, ${loginJpg1800} 1218w`

/**
 * The basalt-cliff photograph behind the sign-in card (CLAUDE.md 16.1: the admin dashboard only, this is not
 * part of the PDF report). Purely decorative (`alt=""`, `aria-hidden`), so it never reaches the accessibility
 * tree and never competes with the form for a screen-reader user's attention.
 *
 * Loaded only where this component is rendered (the auth screens), at `fetchPriority="high"` since it is the
 * largest thing on the page people see first; nowhere else in the app imports it, so it is never fetched once
 * an admin has signed in.
 */
export function LoginBackground() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 overflow-hidden md:static md:h-auto md:w-auto md:basis-2/5 md:self-stretch lg:basis-[55%]"
    >
      <picture>
        <source type="image/webp" srcSet={WEBP_SRCSET} sizes={SIZES} />
        <img
          src={loginJpg1200}
          srcSet={JPG_SRCSET}
          sizes={SIZES}
          alt=""
          aria-hidden="true"
          loading="eager"
          decoding="async"
          fetchPriority="high"
          className="h-full w-full object-cover object-[70%_50%]"
        />
      </picture>
      {/* Phone: the card sits directly on the photo, so the whole image is darkened for contrast. */}
      <div className="absolute inset-0 bg-slate-950/45 md:hidden" />
      {/* Tablet and desktop: only a bottom gradient, so the photo itself stays clear; the tagline sits inside it. */}
      <div className="absolute inset-x-0 bottom-0 hidden h-1/3 bg-gradient-to-t from-slate-950/85 via-slate-950/25 to-transparent md:block" />
      <p className="absolute inset-x-0 bottom-6 hidden items-center justify-center gap-2 px-6 text-center text-xs font-medium tracking-[0.2em] text-white uppercase md:flex">
        <span>Verify</span>
        <span aria-hidden="true" className="text-white/60">
          |
        </span>
        <span>Validate</span>
        <span aria-hidden="true" className="text-white/60">
          |
        </span>
        <span>Trust</span>
      </p>
    </div>
  )
}
