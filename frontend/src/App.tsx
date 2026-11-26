import { BrowserRouter, Routes, Route } from 'react-router';
import { GuestOnlyRoute } from './features/auth/components/GuestOnlyRoute'
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { ProtectedRoute } from './features/auth/components/ProtectedRoute';
import { CreatePostPage } from './pages/CreatePostPage';

import { Header } from './components/layout/Header'
import { Footer } from './components/layout/Footer'
import { PostsPage } from './pages/PostsPage';
import { PostDetailsPage } from './pages/PostDetailsPage'
import { PrivacyPage } from './pages/PrivacyPage';
import { TermsPage } from './pages/TermsPage';
import { CookiesPage } from './pages/CookiesPage';

import { MyPostsPage } from './pages/MyPostsPage';
import { EditPostPage } from './pages/EditPostPage';
import { RegisterPage } from './pages/RegisterPage';
import { AccountPage } from './pages/AccountPage';
import { ConversationPage } from './pages/ConversationPage';
import { MessagesPage } from './pages/MessagesPage';

import { AdminRoute } from './features/auth/components/AdminRoute'
import { AdminReportsPage } from './pages/AdminReportsPage'


function App() {

  return (
    <BrowserRouter>
      <div className="flex min-h-screen flex-col">
        <Header />

        <main className="flex-1 p-6">
          <Routes>
            <Route path="/" element={<HomePage />} />

            <Route element={<GuestOnlyRoute />}>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
            </Route>

            <Route path="/posts" element={<PostsPage />} />
            <Route path="/posts/:id" element={<PostDetailsPage />} />
            <Route path="/privacy" element={<PrivacyPage />} />
            <Route path="/terms" element={<TermsPage />} />
            <Route path="/cookies" element={<CookiesPage />} />
            <Route path="*" element={<h1>404 — Page Not Found</h1>} />

            <Route element={<ProtectedRoute />}>
              <Route path="/posts/create" element={<CreatePostPage />} />
              <Route path="/posts/my" element={<MyPostsPage />} />
              <Route path="/posts/:id/edit" element={<EditPostPage />} />
              <Route path="/account" element={<AccountPage />} />
              <Route path="/messages/:id" element={<ConversationPage />} />
              <Route path="/messages" element={<MessagesPage />} />
            </Route>

            <Route element={<AdminRoute />}>
              <Route path="/admin/reports" element={<AdminReportsPage />} />
            </Route>
          </Routes>
        </main>

        <Footer />
      </div>
    </BrowserRouter>
  )
}

export default App
